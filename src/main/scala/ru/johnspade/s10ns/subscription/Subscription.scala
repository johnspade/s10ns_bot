package ru.johnspade.s10ns.subscription

import java.time.Instant
import java.time.LocalDate

import org.joda.money.CurrencyUnit
import org.joda.money.CurrencyUnit.EUR
import org.joda.money.Money

final case class BillingPeriod(
    duration: Int,
    unit: BillingPeriodUnit
)

case class Subscription(
    id: Long,
    userId: Long,
    name: String,
    amount: Money,
    oneTime: Option[Boolean],
    billingPeriod: Option[BillingPeriod],
    firstPaymentDate: Option[LocalDate],
    sendNotifications: Boolean = false,
    lastNotification: Option[Instant] = None
)

object Subscription {
  def fromDraft(draft: SubscriptionDraft, id: Long): Subscription =
    Subscription(
      id = id,
      userId = draft.userId,
      name = draft.name,
      amount = Money.ofMinor(draft.currency, draft.amount),
      oneTime = draft.oneTime,
      billingPeriod = draft.periodDuration.flatMap { duration =>
        draft.periodUnit.map(BillingPeriod(duration, _))
      },
      firstPaymentDate = draft.firstPaymentDate,
      sendNotifications = draft.sendNotifications
    )
}

case class SubscriptionDraft(
    userId: Long,
    name: String,
    currency: CurrencyUnit,
    amount: Long,
    oneTime: Option[Boolean],
    periodDuration: Option[Int],
    periodUnit: Option[BillingPeriodUnit],
    firstPaymentDate: Option[LocalDate],
    sendNotifications: Boolean = false
)

object SubscriptionDraft {
  def create(userId: Long, currency: CurrencyUnit = EUR): SubscriptionDraft =
    SubscriptionDraft(
      userId = userId,
      name = "",
      currency = currency,
      amount = 0L,
      oneTime = None,
      periodDuration = None,
      periodUnit = None,
      firstPaymentDate = None
    )
}
