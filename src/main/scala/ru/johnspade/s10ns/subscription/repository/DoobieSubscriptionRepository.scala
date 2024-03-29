package ru.johnspade.s10ns.subscription.repository

import java.time.Instant
import scala.annotation.nowarn

import doobie.Query0
import doobie.Update0
import doobie.free.connection
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.implicits.legacy.instant._
import doobie.implicits.legacy.localdate._
import doobie.util.Read
import doobie.util.Write
import org.joda.money.CurrencyUnit
import org.joda.money.Money

import ru.johnspade.s10ns.subscription.Subscription
import ru.johnspade.s10ns.subscription.SubscriptionDraft
import ru.johnspade.s10ns.subscription.repository.DoobieSubscriptionRepository.SubscriptionSql
import ru.johnspade.s10ns.user.DoobieUserMeta._
import ru.johnspade.s10ns.user.User

class DoobieSubscriptionRepository extends SubscriptionRepository[ConnectionIO] {
  override def create(draft: SubscriptionDraft): ConnectionIO[Subscription] =
    SubscriptionSql
      .create(draft)
      .withUniqueGeneratedKeys[Long]("id")
      .map(id => Subscription.fromDraft(draft, id))

  override def getById(id: Long): ConnectionIO[Option[Subscription]] =
    SubscriptionSql
      .get(id)
      .option

  def getByIdWithUser(id: Long): ConnectionIO[Option[(Subscription, User)]] =
    SubscriptionSql
      .getWithUser(id)
      .option

  override def getByUserId(userId: Long): ConnectionIO[List[Subscription]] =
    SubscriptionSql
      .getByUserId(userId)
      .to[List]

  def collectNotifiable(cutoff: Instant): ConnectionIO[List[Subscription]] =
    SubscriptionSql
      .collectNotifiable(cutoff)
      .to[List]

  override def remove(id: Long): ConnectionIO[Unit] = SubscriptionSql.remove(id).run.map(_ => ())

  override def update(s10n: Subscription): ConnectionIO[Option[Subscription]] =
    for {
      oldS10n <- SubscriptionSql.get(s10n.id).option
      newS10n = oldS10n.map(_ => s10n)
      _ <- newS10n.fold(connection.unit)(SubscriptionSql.update(_).run.map(_ => ()))
    } yield newS10n

  override def disableNotificationsForUser(userId: Long): ConnectionIO[Unit] =
    SubscriptionSql
      .disableNotifications(userId)
      .run
      .map(_ => ())
}

object DoobieSubscriptionRepository {
  object SubscriptionSql {
    def create(draft: SubscriptionDraft): Update0 = {
      import draft._

      sql"""
        insert into subscriptions
        (user_id, name, amount, currency, one_time, period_duration, period_unit, first_payment_date, send_notifications)
        values ($userId, $name, $amount, ${currency.getCode}, $oneTime, $periodDuration, $periodUnit, $firstPaymentDate, $sendNotifications)
      """.update
    }

    def get(id: Long): Query0[Subscription] = sql"""
        select id, user_id, name, amount, currency, one_time, period_duration, period_unit, first_payment_date, send_notifications, last_notification
        from subscriptions
        where id = $id
      """.query[Subscription]

    def getWithUser(id: Long): Query0[(Subscription, User)] =
      sql"""
        select s.id, s.user_id, s.name, s.amount, s.currency, s.one_time, s.period_duration, s.period_unit,
        s.first_payment_date, s.send_notifications, s.last_notification, u.id, u.first_name, u.chat_id,
        u.default_currency, u.dialog, u.notify_by_default
        from subscriptions s
        left join users u on s.user_id = u.id
        where s.id = $id
      """.query[(Subscription, User)]

    def getByUserId(userId: Long): Query0[Subscription] = sql"""
        select id, user_id, name, amount, currency, one_time, period_duration, period_unit, first_payment_date, send_notifications, last_notification
        from subscriptions
        where user_id = $userId
      """.query[Subscription]

    def collectNotifiable(cutoff: Instant): Query0[Subscription] =
      sql"""
        select id, user_id, name, amount, currency, one_time, period_duration, period_unit, first_payment_date, send_notifications, last_notification
        from subscriptions
        where send_notifications = true
        and (
          (first_payment_date is not null and period_unit is not null and period_duration is not null)
          or (first_payment_date is not null and first_payment_date > $cutoff)
        )
      """.query[Subscription]

    def remove(id: Long): Update0 = sql"delete from subscriptions where id = $id".update

    def update(s10n: Subscription): Update0 = {
      import s10n._

      sql"""
        update subscriptions set
        user_id = $userId,
        name = $name,
        amount = ${amount.getAmountMinorLong},
        currency = ${amount.getCurrencyUnit.getCode},
        one_time = $oneTime,
        period_duration = ${billingPeriod.map(_.duration)},
        period_unit = ${billingPeriod.map(_.unit)},
        first_payment_date = $firstPaymentDate,
        send_notifications = $sendNotifications,
        last_notification = $lastNotification
        where id = $id
      """.update
    }

    def disableNotifications(userId: Long): Update0 =
      sql"""
        update subscriptions
        set send_notifications = false
        where user_id = $userId and send_notifications = true
      """.update
  }

  @nowarn
  private implicit val moneyRead: Read[Money] =
    Read[(Long, String)].map { case (amountMinor, currencyUnit) =>
      Money.ofMinor(CurrencyUnit.of(currencyUnit), amountMinor)
    }
  @nowarn
  private implicit val moneyWrite: Write[Money] =
    Write[(Long, String)].contramap(m => (m.getAmountMinorLong, m.getCurrencyUnit.getCode))
}
