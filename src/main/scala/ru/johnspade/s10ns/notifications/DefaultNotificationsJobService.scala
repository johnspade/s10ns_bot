package ru.johnspade.s10ns.notifications

import java.time.Instant

import cats.data.OptionT
import cats.effect.{Clock, Concurrent, Timer}
import cats.implicits._
import cats.{Monad, ~>}
import ru.johnspade.s10ns.repeat
import ru.johnspade.s10ns.subscription.Subscription
import ru.johnspade.s10ns.subscription.repository.SubscriptionRepository
import ru.johnspade.s10ns.user.User
import telegramium.bots.ChatIntId
import telegramium.bots.client.{Api, SendMessageReq}
import tofu.logging._
import tofu.syntax.logging._

import scala.concurrent.duration._

class DefaultNotificationsJobService[F[_]: Concurrent: Clock: Timer: Logging, D[_]: Monad](
  private val notificationRepo: NotificationRepository[D],
  private val s10nRepo: SubscriptionRepository[D]
)(private implicit val transact: D ~> F) extends NotificationsJobService[F] {
  def startNotificationsJob()(implicit bot: Api[F]): F[Unit] = {
    def sendNotification(s10nWithUser: (Subscription, User), timestamp: Instant) = {
      val (s10n, user) = s10nWithUser
      user.chatId.map { chatId =>
        bot.sendMessage(SendMessageReq(
          chatId = ChatIntId(chatId),
          text = s"A deadline for ${s10n.name} is approaching" // todo
        ))
          .void
      }
        .sequence_
        .flatMap(_ => info"Notification for ${s10n.toString} sent")
        .unlessA(isNotified(s10nWithUser._1, timestamp))
    }

    def executeTask(): F[Unit] =
      Clock[F].realTime(MILLISECONDS).map(Instant.ofEpochMilli).flatMap { now =>
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

    Concurrent[F].start {
      repeat(executeTask(), 30.seconds)
    }
      .void
  }
}

object DefaultNotificationsJobService {
  def apply[F[_]: Concurrent: Timer, D[_]: Monad](
    notificationRepo: NotificationRepository[D],
    s10nRepo: SubscriptionRepository[D]
  )(implicit transact: D ~> F, logs: Logs[F, F]): F[DefaultNotificationsJobService[F, D]] =
    logs.forService[DefaultNotificationsJobService[D, D]].map { implicit l =>
      new DefaultNotificationsJobService[F, D](
        notificationRepo,
        s10nRepo
      )
    }
}
