package ru.johnspade.s10ns.subscription.repository

import java.time.Instant

import doobie.free.connection
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.implicits.legacy.instant._
import doobie.implicits.legacy.localdate._
import doobie.postgres.implicits._
import doobie.Query0
import doobie.Update0
import doobie.util.{Read, Write}
import org.joda.money.{CurrencyUnit, Money}
import ru.johnspade.s10ns.subscription.repository.DoobieSubscriptionRepository.SubscriptionSql
import ru.johnspade.s10ns.subscription.tags._
import ru.johnspade.s10ns.subscription.{Subscription, SubscriptionDraft}
import ru.johnspade.s10ns.user.User
import ru.johnspade.s10ns.user.tags._
import ru.johnspade.s10ns.user.DoobieUserMeta._

class DoobieSubscriptionRepository extends SubscriptionRepository[ConnectionIO] {
  override def create(draft: SubscriptionDraft): ConnectionIO[Subscription] =
    SubscriptionSql
      .create(draft)
      .withUniqueGeneratedKeys[Long]("id")
      .map(id => Subscription.fromDraft(draft, SubscriptionId(id)))

  override def getById(id: SubscriptionId): ConnectionIO[Option[Subscription]] =
    SubscriptionSql
      .get(id)
      .option

  def getByIdWithUser(id: SubscriptionId): ConnectionIO[Option[(Subscription, User)]] =
    SubscriptionSql
    .getWithUser(id)
    .option

  override def getByUserId(userId: UserId): ConnectionIO[List[Subscription]] =
    SubscriptionSql
      .getByUserId(userId)
      .to[List]

  def collectNotifiable(cutoff: Instant): ConnectionIO[List[Subscription]] =
    SubscriptionSql
      .collectNotifiable(cutoff)
      .to[List]

  override def remove(id: SubscriptionId): ConnectionIO[Unit] = SubscriptionSql.remove(id).run.map(_ => ())

  override def update(s10n: Subscription): ConnectionIO[Option[Subscription]] =
    for {
      oldS10n <- SubscriptionSql.get(s10n.id).option
      newS10n = oldS10n.map(_ => s10n)
      _ <- newS10n.fold(connection.unit)(SubscriptionSql.update(_).run.map(_ => ()))
    } yield newS10n

  override def disableNotificationsForUser(userId: UserId): ConnectionIO[Unit] =
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

    def get(id: SubscriptionId): Query0[Subscription] = sql"""
        select id, user_id, name, amount, currency, one_time, period_duration, period_unit, first_payment_date, send_notifications, last_notification
        from subscriptions
        where id = $id
      """.query[Subscription]

    def getWithUser(id: SubscriptionId): Query0[(Subscription, User)] =
      sql"""
        select s.id, s.user_id, s.name, s.amount, s.currency, s.one_time, s.period_duration, s.period_unit,
        s.first_payment_date, s.send_notifications, s.last_notification, u.id, u.first_name, u.chat_id,
        u.default_currency, u.dialog
        from subscriptions s
        left join users u on s.user_id = u.id
        where s.id = $id
      """.query[(Subscription, User)]

    def getByUserId(userId: UserId): Query0[Subscription] = sql"""
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

    def remove(id: SubscriptionId): Update0 = sql"delete from subscriptions where id = $id".update

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

    def disableNotifications(userId: UserId): Update0 =
      sql"""
        update subscriptions
        set send_notifications = false
        where user_id = $userId and send_notifications = true
      """.update
  }

  private implicit val moneyRead: Read[Money] =
    Read[(Long, String)].map {
      case (amountMinor, currencyUnit) => Money.ofMinor(CurrencyUnit.of(currencyUnit), amountMinor)
    }
  private implicit val moneyWrite: Write[Money] =
    Write[(Long, String)].contramap(m => (m.getAmountMinorLong, m.getCurrencyUnit.getCode))
}
