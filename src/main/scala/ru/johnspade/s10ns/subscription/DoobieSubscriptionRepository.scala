package ru.johnspade.s10ns.subscription

import java.time.temporal.ChronoUnit

import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.{Meta, Read, Write}
import doobie.util.log.LogHandler
import doobie.util.query.Query0
import doobie.util.update.Update0
import org.joda.money.{CurrencyUnit, Money}
import ru.johnspade.s10ns.subscription.DoobieSubscriptionRepository.SubscriptionSql
import ru.johnspade.s10ns.user.UserId

class DoobieSubscriptionRepository extends SubscriptionRepository {
  override def create(draft: SubscriptionDraft): ConnectionIO[Subscription] =
    SubscriptionSql
      .create(draft)
      .withUniqueGeneratedKeys[Long]("id")
      .map(id => Subscription.fromDraft(draft, SubscriptionId(id)))

  override def getById(id: SubscriptionId): ConnectionIO[Option[Subscription]] =
    SubscriptionSql
      .get(id)
      .option

  override def getByUserId(userId: UserId): ConnectionIO[List[Subscription]] =
    SubscriptionSql
      .getByUserId(userId)
      .to[List]

  override def remove(id: SubscriptionId): ConnectionIO[Unit] = SubscriptionSql.remove(id).run.map(_ => ())
}

object DoobieSubscriptionRepository {
  private object SubscriptionSql {
    private implicit val han: LogHandler = LogHandler.jdkLogHandler

    def create(draft: SubscriptionDraft): Update0 = {
      import draft._

      sql"""
        insert into subscriptions
        (user_id, name, amount, currency, one_time, period_duration, period_unit, first_payment_date)
        values (${userId.value}, ${name.value}, $amount, ${currency.getCode}, ${oneTime.value},
        ${periodDuration.map(_.value)}, ${periodUnit.map(_.value)}, $firstPaymentDate)
      """.update
    }

    def get(id: SubscriptionId): Query0[Subscription] = sql"""
        select id, user_id, name, amount, currency, description, one_time, period_duration, period_unit, first_payment_date
        from subscriptions
        where id = ${id.value}
      """.query[Subscription]

    def getByUserId(userId: UserId): Query0[Subscription] = sql"""
        select id, user_id, name, amount, currency, description, one_time, period_duration, period_unit, first_payment_date
        from subscriptions
        where user_id = ${userId.value}
      """.query[Subscription]

    def remove(id: SubscriptionId): Update0 = sql"delete from subscriptions where id = ${id.value}".update
  }

  private implicit val moneyRead: Read[Money] =
    Read[(Long, String)].map {
      case (amountMinor, currencyUnit) => Money.ofMinor(CurrencyUnit.of(currencyUnit), amountMinor)
    }
  private implicit val moneyWrite: Write[Money] =
    Write[(Long, String)].contramap(m => (m.getAmountMinorLong, m.getCurrencyUnit.getCode))
  private implicit val chronoUnitMeta: Meta[ChronoUnit] = Meta[String].timap(ChronoUnit.valueOf)(_.name())
}


