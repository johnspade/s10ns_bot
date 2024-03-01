package ru.johnspade.s10ns.notifications

import java.time.Instant
import scala.concurrent.duration._

import cats.Monad
import cats.effect.Async
import cats.effect.Concurrent
import cats.implicits._
import cats.~>

import io.chrisdavenport.fuuid.FUUID
import tofu.logging._
import tofu.syntax.logging._

import ru.johnspade.s10ns.currentTimestamp
import ru.johnspade.s10ns.repeat
import ru.johnspade.s10ns.subscription.Subscription
import ru.johnspade.s10ns.subscription.repository.SubscriptionRepository

class DefaultPrepareNotificationsJobService[F[_]: Concurrent: Async: Logging, D[_]: Monad](
  private val s10nRepo: SubscriptionRepository[D],
  private val notificationRepo: NotificationRepository[D],
  private val notificationsService: NotificationService[F]
)(private implicit val transact: D ~> F) extends PrepareNotificationsJobService[F] {
  def prepareNotifications(): F[Unit] = {
    def createNotifications(s10ns: List[Subscription], cutoff: Instant): F[List[Notification]] =
      s10ns.traverse { s10n =>
        notificationsService.needNotification(s10n, cutoff).flatMap {
          Option.when(_) {
            FUUID.randomFUUID[F].map(Notification(_, s10n.id))
          }
            .sequence
        }
      }
        .map(_.flatten)

    for {
      now <- currentTimestamp
      notifiable <- transact(s10nRepo.collectNotifiable(now))
      notifications <- createNotifications(notifiable, now)
      _ <- info"Notifiable: ${notifications.map(_.subscriptionId).mkString(", ")}".unlessA(notifications.isEmpty)
      _ <- transact(notificationRepo.create(notifications).unlessA(notifications.isEmpty))
    } yield ()
  }

  def startPrepareNotificationsJob(): F[Unit] =
    Concurrent[F].start {
      repeat(prepareNotifications(), 30.minutes)
    }
      .void
}

object DefaultPrepareNotificationsJobService {
  def apply[F[_]: Concurrent: Async, D[_]: Monad](
    subscriptionRepo: SubscriptionRepository[D],
    notificationRepo: NotificationRepository[D],
    notificationService: NotificationService[F]
  )(implicit transact: D ~> F, logs: Logs[F, F]): F[DefaultPrepareNotificationsJobService[F, D]] =
    logs.forService[DefaultPrepareNotificationsJobService[F, D]].map { implicit l =>
      new DefaultPrepareNotificationsJobService[F, D](subscriptionRepo, notificationRepo, notificationService)
    }
}
