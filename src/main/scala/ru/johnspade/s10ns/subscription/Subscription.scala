package ru.johnspade.s10ns.subscription

import org.joda.money.CurrencyUnit.EUR
import org.joda.money.{CurrencyUnit, Money}
import ru.johnspade.s10ns.subscription.tags._
import ru.johnspade.s10ns.user.tags._

final case class BillingPeriod(
  duration: BillingPeriodDuration,
  unit: BillingPeriodUnit
)

case class Subscription(
  id: SubscriptionId,
  userId: UserId,
  name: SubscriptionName,
  amount: Money,
  oneTime: Option[OneTimeSubscription],
  billingPeriod: Option[BillingPeriod],
  firstPaymentDate: Option[FirstPaymentDate],
  sendNotifications: Boolean = false
)

object Subscription {
  def fromDraft(draft: SubscriptionDraft, id: SubscriptionId): Subscription =
    Subscription(
      id = id,
      userId = draft.userId,
      name = draft.name,
      amount = Money.ofMinor(draft.currency, draft.amount),
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
  oneTime: Option[OneTimeSubscription],
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
      amount = SubscriptionAmount(0L),
      oneTime = None,
      periodDuration = None,
      periodUnit = None,
      firstPaymentDate = None
    )
}
