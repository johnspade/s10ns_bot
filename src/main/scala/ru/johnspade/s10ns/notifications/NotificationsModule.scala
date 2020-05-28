package ru.johnspade.s10ns.notifications

import cats.effect.{Clock, Concurrent, Timer}
import cats.implicits._
import cats.~>
import doobie.free.connection.ConnectionIO
import ru.johnspade.s10ns.subscription.SubscriptionModule
import tofu.logging.Logs

final class NotificationsModule[F[_], D[_]] private(
  val prepareNotificationsJobService: PrepareNotificationsJobService[F],
  val notificationsJobService: NotificationsJobService[F]
)

object NotificationsModule {
  def make[F[_]: Concurrent: Timer: Clock](
    subscriptionModule: SubscriptionModule[F, ConnectionIO]
  )(implicit transact: ConnectionIO ~> F, logs: Logs[F, F]): F[NotificationsModule[F, ConnectionIO]] = {
    import subscriptionModule.{subscriptionRepository, s10nInfoService}

    val notificationRepo = new DoobieNotificationRepository
    for {
      prepareNotificationsJobService <- DefaultPrepareNotificationsJobService[F, ConnectionIO](
        subscriptionRepository,
        notificationRepo,
        s10nInfoService
      )
      notificationsJobService <- DefaultNotificationsJobService[F, ConnectionIO](
        notificationRepo,
        subscriptionRepository
      )
    } yield new NotificationsModule[F, ConnectionIO](
      prepareNotificationsJobService = prepareNotificationsJobService,
      notificationsJobService = notificationsJobService
    )
  }
}
