package ru.johnspade.s10ns.notifications

import java.time.Instant

import cats.effect.{Clock, Concurrent, Timer}
import cats.implicits._
import cats.{Monad, ~>}
import io.chrisdavenport.fuuid.FUUID
import ru.johnspade.s10ns.repeat
import ru.johnspade.s10ns.subscription.Subscription
import ru.johnspade.s10ns.subscription.repository.SubscriptionRepository
import ru.johnspade.s10ns.subscription.service.S10nInfoService
import tofu.logging._
import tofu.syntax.logging._

import scala.concurrent.duration._

class DefaultPrepareNotificationsJobService[F[_]: Concurrent: Clock: Timer: Logging, D[_]: Monad](
  private val s10nRepo: SubscriptionRepository[D],
  private val notificationRepo: NotificationRepository[D],
  private val s10nInfoService: S10nInfoService[F]
)(private implicit val transact: D ~> F) extends PrepareNotificationsJobService[F] {
  def startPrepareNotificationsJob(): F[Unit] = {
    def createNotifications(s10ns: List[Subscription], cutoff: Instant): F[List[Notification]] =
      s10ns.mapFilter { s10n =>
        s10n.firstPaymentDate.map { start =>
          s10nInfoService.getNextPaymentTimestamp(start, s10n.billingPeriod).flatMap { timestamp =>
            val needNotification = lessThan23Hours(cutoff, timestamp)
            val notified = isNotified(s10n, timestamp)
            Option.when(needNotification && !notified) {
              FUUID.randomFUUID[F].map((id: FUUID) => Notification(id, 3, s10n.id))
            }
              .sequence
          }
        }
      }
        .sequence.map(_.flatten)

    def prepareNotifications(): F[Unit] =
      for {
        millis <- Clock[F].realTime(MILLISECONDS)
        now = Instant.ofEpochMilli(millis)
        notifiable <- transact(s10nRepo.collectNotifiable(now))
        notifications <- createNotifications(notifiable, now)
        _ <- info"Notifiable: ${notifications.map(_.subscriptionId).mkString(", ")}".unlessA(notifications.isEmpty)
        _ <- transact(notificationRepo.create(notifications).unlessA(notifications.isEmpty))
      } yield ()

    Concurrent[F].start {
      repeat(prepareNotifications(), 30.minutes)
    }
      .void
  }
}

object DefaultPrepareNotificationsJobService {
  def apply[F[_]: Concurrent: Clock: Timer, D[_]: Monad](
    subscriptionRepo: SubscriptionRepository[D],
    notificationRepo: NotificationRepository[D],
    s10nInfoService: S10nInfoService[F]
  )(implicit transact: D ~> F, logs: Logs[F, F]): F[DefaultPrepareNotificationsJobService[F, D]] =
    logs.forService[DefaultPrepareNotificationsJobService[F, D]].map { implicit l =>
      new DefaultPrepareNotificationsJobService[F, D](subscriptionRepo, notificationRepo, s10nInfoService)
    }
}
