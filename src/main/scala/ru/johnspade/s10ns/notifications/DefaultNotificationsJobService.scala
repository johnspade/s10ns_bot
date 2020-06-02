package ru.johnspade.s10ns.notifications

import java.time.Instant

import cats.data.OptionT
import cats.effect.{Clock, Concurrent, Timer}
import cats.implicits._
import cats.{Monad, ~>}
import ru.johnspade.s10ns.{currentTimestamp, repeat}
import ru.johnspade.s10ns.subscription.Subscription
import ru.johnspade.s10ns.subscription.repository.SubscriptionRepository
import ru.johnspade.s10ns.subscription.service.S10nsListMessageService
import ru.johnspade.s10ns.user.User
import telegramium.bots.ChatIntId
import telegramium.bots.client.{Api, SendMessageReq}
import tofu.logging._
import tofu.syntax.logging._

import scala.concurrent.duration._

class DefaultNotificationsJobService[F[_]: Concurrent: Clock: Timer: Logging, D[_]: Monad](
  private val notificationRepo: NotificationRepository[D],
  private val s10nRepo: SubscriptionRepository[D],
  private val s10nListMessageService: S10nsListMessageService[F],
  private val notificationService: NotificationService[F]
)(private implicit val transact: D ~> F) extends NotificationsJobService[F] {
  def executeTask()(implicit bot: Api[F]): F[Unit] = {
    def sendNotification(s10nWithUser: (Subscription, User), timestamp: Instant) = {
      val (s10n, user) = s10nWithUser
      user.chatId.map { chatId =>
        s10nListMessageService.createSubscriptionMessage(user.defaultCurrency, s10n)
          .flatMap { message =>
            bot.sendMessage(SendMessageReq(
              chatId = ChatIntId(chatId),
              text = s"_A payment date is approaching:_\n${message.text}",
              parseMode = message.parseMode,
              replyMarkup = message.markup
            ))
              .void
          }
      }
        .sequence_
        .flatMap(_ => info"Notification for ${s10n.toString} sent")
        .unlessA(notificationService.isNotified(s10nWithUser._1, timestamp))
    }

    currentTimestamp.flatMap { now =>
      transact {
        (for {
          notification <- OptionT(notificationRepo.retrieveForSending())
          s10nWithUser <- OptionT(s10nRepo.getByIdWithUser(notification.subscriptionId))
          sendNotificationF <- OptionT.pure[D](sendNotification(s10nWithUser, now))
          _ <- OptionT(s10nRepo.update(s10nWithUser._1.copy(lastNotification = now.some)))
          _ <- OptionT.liftF(notificationRepo.delete(notification.id))
        } yield sendNotificationF)
          .value
      }
    }
      .flatMap(_.sequence_)
  }

  def startNotificationsJob()(implicit bot: Api[F]): F[Unit] =
    Concurrent[F].start {
    repeat(executeTask(), 30.seconds)
  }
    .void
}

object DefaultNotificationsJobService {
  def apply[F[_]: Concurrent: Timer, D[_]: Monad](
    notificationRepo: NotificationRepository[D],
    s10nRepo: SubscriptionRepository[D],
    s10nsListMessageService: S10nsListMessageService[F],
    notificationService: NotificationService[F]
  )(implicit transact: D ~> F, logs: Logs[F, F]): F[DefaultNotificationsJobService[F, D]] =
    logs.forService[DefaultNotificationsJobService[D, D]].map { implicit l =>
      new DefaultNotificationsJobService[F, D](notificationRepo, s10nRepo, s10nsListMessageService, notificationService)
    }
}
