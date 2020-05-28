package ru.johnspade.s10ns.notifications

import cats.instances.list.catsStdInstancesForList
import cats.syntax.functor._
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.implicits.legacy.instant._
import doobie.postgres.implicits._
import doobie.util.update.Update
import doobie.{Query0, Update0}
import io.chrisdavenport.fuuid.FUUID
import io.chrisdavenport.fuuid.doobie.implicits._
import ru.johnspade.s10ns.notifications.DoobieNotificationRepository.NotificationSql
import ru.johnspade.s10ns.subscription.tags.SubscriptionId

class DoobieNotificationRepository extends NotificationRepository[ConnectionIO] {
  override def create(notification: Notification): ConnectionIO[Unit] =
    NotificationSql.create(notification).run.void

  def create(notifications: List[Notification]): ConnectionIO[Unit] =
    NotificationSql.create.updateMany(notifications.map(Notification.unapply(_).get)).void

  def retrieveForSending(): ConnectionIO[Option[Notification]] =
    NotificationSql.retrieveForSending().option

  def delete(id: FUUID): ConnectionIO[Unit] =
    NotificationSql.delete(id).run.void
}

object DoobieNotificationRepository {
  object NotificationSql {
    type NotificationInfo = (FUUID, Int, SubscriptionId)

    def create(notification: Notification): Update0 = {
      import notification._

      sql"""
        insert into notifications (id, retries_remaining, subscription_id)
        values ($id, $retriesRemaining, $subscriptionId)
      """.update
    }

    val create: Update[NotificationInfo] = Update[NotificationInfo](
      """
        insert into notifications (id, retries_remaining, subscription_id) values (?, ?, ?)
        on conflict do nothing
      """
    )

    def retrieveForSending(): Query0[Notification] =
      sql"""
        update notifications n1 set retries_remaining = retries_remaining - 1
        where n1.id = (
          select n2.id from notifications n2
          where n2.retries_remaining > 0
          for update skip locked limit 1
        )
        returning (n1.*)
      """.query[Notification]

    def delete(id: FUUID): Update0 =
      sql"""
        delete from notifications where id = $id
      """.update
  }
}
