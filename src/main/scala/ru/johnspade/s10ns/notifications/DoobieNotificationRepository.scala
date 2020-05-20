package ru.johnspade.s10ns.notifications

import doobie.free.connection.ConnectionIO
import doobie.implicits._
import cats.syntax.functor._
import doobie.util.update.Update0
import doobie.implicits.legacy.instant._
import doobie.postgres.implicits._
import ru.johnspade.s10ns.notifications.DoobieNotificationRepository.NotificationSql

class DoobieNotificationRepository extends NotificationRepository[ConnectionIO] {
  override def create(notification: Notification): ConnectionIO[Unit] =
    NotificationSql.create(notification).run.void
}

object DoobieNotificationRepository {
  object NotificationSql {
    def create(notification: Notification): Update0 = {
      import notification._

      sql"""
        insert into notifications (id, notification_timestamp, retries_remaining, subscription_id)
        values ($id, $timestamp, $retriesRemaining, $subscriptionId)
      """.update
    }
  }
}
