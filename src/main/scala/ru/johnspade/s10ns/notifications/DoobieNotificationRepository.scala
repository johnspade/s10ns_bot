package ru.johnspade.s10ns.notifications

import java.util.UUID

import cats.instances.list.catsStdInstancesForList
import cats.syntax.functor._

import doobie.Query0
import doobie.Update0
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.implicits.legacy.instant._
import doobie.postgres.implicits._
import doobie.util.update.Update

import ru.johnspade.s10ns.notifications.DoobieNotificationRepository.NotificationSql

class DoobieNotificationRepository extends NotificationRepository[ConnectionIO] {
  override def create(notification: Notification): ConnectionIO[Unit] =
    NotificationSql.create(notification).run.void

  def create(notifications: List[Notification]): ConnectionIO[Unit] =
    NotificationSql.createMany.updateMany(notifications.map(Notification.unapply(_).get)).void

  def retrieveForSending(): ConnectionIO[Option[Notification]] =
    NotificationSql.retrieveForSending().option

  def delete(id: UUID): ConnectionIO[Unit] =
    NotificationSql.delete(id).run.void

  def getAll: ConnectionIO[List[Notification]] = NotificationSql.getAll.to[List]
}

object DoobieNotificationRepository {
  object NotificationSql {
    type NotificationInfo = (UUID, Long, Int)

    def create(notification: Notification): Update0 = {
      import notification._

      sql"""
        insert into notifications (id, subscription_id, retries_remaining)
        values ($id, $subscriptionId, $retriesRemaining)
      """.update
    }

    val createMany: Update[NotificationInfo] = Update[NotificationInfo](
      """
        insert into notifications (id, subscription_id, retries_remaining) values (?, ?, ?)
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
        returning n1.id, n1.subscription_id, n1.retries_remaining
      """.query[Notification]

    def delete(id: UUID): Update0 =
      sql"""
        delete from notifications where id = $id
      """.update

    def getAll: Query0[Notification] =
      sql"select id, subscription_id, retries_remaining from notifications".query[Notification]
  }
}
