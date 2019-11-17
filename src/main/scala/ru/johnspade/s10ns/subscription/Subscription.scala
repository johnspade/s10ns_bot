package ru.johnspade.s10ns.subscription

import java.time.LocalDate
import java.time.temporal.ChronoUnit

import org.joda.money.{CurrencyUnit, Money}
import org.joda.money.CurrencyUnit.EUR
import ru.johnspade.s10ns.user.UserId

final case class SubscriptionId(value: Long) extends AnyVal
final case class SubscriptionName(value: String) extends AnyVal
final case class SubscriptionAmount(value: Long) extends AnyVal
final case class SubscriptionDescription(value: String) extends AnyVal
final case class OneTimeSubscription(value: Boolean) extends AnyVal
final case class BillingPeriodDuration(value: Int) extends AnyVal
final case class BillingPeriodUnit(value: ChronoUnit) extends AnyVal
final case class FirstPaymentDate(value: LocalDate) extends AnyVal

final case class BillingPeriod(duration: BillingPeriodDuration, unit: BillingPeriodUnit)

case class Subscription(
  id: SubscriptionId,
  userId: UserId,
  name: SubscriptionName,
  amount: Money,
  description: Option[SubscriptionDescription],
  oneTime: OneTimeSubscription,
  billingPeriod: Option[BillingPeriod],
  firstPaymentDate: Option[FirstPaymentDate]
)

object Subscription {
  def fromDraft(draft: SubscriptionDraft, id: SubscriptionId): Subscription =
    Subscription(
      id = id,
      userId = draft.userId,
      name = draft.name,
      amount = Money.ofMinor(draft.currency, draft.amount.value),
      description = draft.description,
      oneTime = draft.oneTime,
      billingPeriod = draft.periodDuration.flatMap { duration =>
        draft.periodUnit.map(BillingPeriod(duration, _))
      },
      firstPaymentDate = draft.firstPaymentDate
    )
}

case class SubscriptionDraft(
  userId: UserId,
  name: SubscriptionName,
  currency: CurrencyUnit,
  amount: SubscriptionAmount,
  description: Option[SubscriptionDescription],
  oneTime: OneTimeSubscription,
  periodDuration: Option[BillingPeriodDuration],
  periodUnit: Option[BillingPeriodUnit],
  firstPaymentDate: Option[FirstPaymentDate]
)

object SubscriptionDraft {
  def create(userId: UserId, currency: CurrencyUnit = EUR): SubscriptionDraft =
    SubscriptionDraft(
      userId = userId,
      name = SubscriptionName(""),
      currency = currency,
      amount = SubscriptionAmount(0),
      description = None,
      oneTime = OneTimeSubscription(false),
      periodDuration = None,
      periodUnit = None,
      firstPaymentDate = None
    )
}
