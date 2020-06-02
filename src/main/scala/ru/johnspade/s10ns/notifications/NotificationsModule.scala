package ru.johnspade.s10ns.notifications

import cats.effect.{Clock, Concurrent, Timer}
import cats.syntax.flatMap._
import cats.syntax.functor._
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
    import subscriptionModule.{s10nInfoService, s10nsListMessageService, subscriptionRepository}

    val notificationRepo = new DoobieNotificationRepository
    val notificationService = new NotificationService[F](
      hoursBefore = 23,
      s10nInfoService
    )
    for {
      prepareNotificationsJobService <- DefaultPrepareNotificationsJobService[F, ConnectionIO](
        subscriptionRepository,
        notificationRepo,
        notificationService
      )
      notificationsJobService <- DefaultNotificationsJobService[F, ConnectionIO](
        notificationRepo,
        subscriptionRepository,
        s10nsListMessageService,
        notificationService
      )
    } yield new NotificationsModule[F, ConnectionIO](
      prepareNotificationsJobService = prepareNotificationsJobService,
      notificationsJobService = notificationsJobService
    )
  }
}
