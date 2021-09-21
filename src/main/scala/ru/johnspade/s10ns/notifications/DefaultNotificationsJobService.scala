package ru.johnspade.s10ns.notifications

import cats.data.OptionT
import cats.effect.{Concurrent, Temporal}
import cats.syntax.applicative._
import cats.syntax.applicativeError._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.syntax.option._
import cats.{Monad, MonadError, ~>}
import ru.johnspade.s10ns.subscription.Subscription
import ru.johnspade.s10ns.subscription.repository.SubscriptionRepository
import ru.johnspade.s10ns.subscription.service.S10nsListMessageService
import ru.johnspade.s10ns.user.User
import ru.johnspade.s10ns.{currentTimestamp, repeat}
import telegramium.bots.ChatIntId
import telegramium.bots.high.Methods._
import telegramium.bots.high.implicits._
import telegramium.bots.high.{Api, FailedRequest}
import tofu.logging._
import tofu.syntax.logging._

import java.time.Instant
import scala.concurrent.duration._

class DefaultNotificationsJobService[F[_]: Concurrent: Temporal: Logging, D[_]: Monad](
  private val notificationRepo: NotificationRepository[D],
  private val s10nRepo: SubscriptionRepository[D],
  private val s10nListMessageService: S10nsListMessageService[F],
  private val notificationService: NotificationService[F]
)(implicit transact: D ~> F, monadError: MonadError[F, Throwable]) extends NotificationsJobService[F] {
  def executeTask()(implicit bot: Api[F]): F[Unit] = {
    def sendNotification(s10nWithUser: (Subscription, User), timestamp: Instant) = {
      val (s10n, user) = s10nWithUser
      user.chatId.map { chatId =>
        s10nListMessageService.createSubscriptionMessage(user.defaultCurrency, s10n)
          .flatMap { message =>
            sendMessage(
              chatId = ChatIntId(chatId),
              text = s"_A payment date is approaching:_\n${message.text}",
              parseMode = message.parseMode,
              replyMarkup = message.markup
            )
              .exec
              .void
              .recoverWith { case FailedRequest(_, Some(403), Some("Forbidden: bot was blocked by the user")) =>
                warn"Bot was blocked by the user ${user.id.toString}, disabling notifications" *>
                  transact(s10nRepo.disableNotificationsForUser(user.id))
              }
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
  def apply[F[_]: Concurrent: Temporal, D[_]: Monad](
    notificationRepo: NotificationRepository[D],
    s10nRepo: SubscriptionRepository[D],
    s10nsListMessageService: S10nsListMessageService[F],
    notificationService: NotificationService[F]
  )(implicit transact: D ~> F, logs: Logs[F, F]): F[DefaultNotificationsJobService[F, D]] =
    logs.forService[DefaultNotificationsJobService[D, D]].map { implicit l =>
      new DefaultNotificationsJobService[F, D](notificationRepo, s10nRepo, s10nsListMessageService, notificationService)
    }
}
