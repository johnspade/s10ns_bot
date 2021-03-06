package ru.johnspade.s10ns.subscription

import java.time.LocalDate

import org.joda.money.Money
import ru.johnspade.s10ns.subscription.tags.{FirstPaymentDate, OneTimeSubscription, PageNumber, SubscriptionId}

final case class S10nInfo(
  id: SubscriptionId,
  name: String,
  amount: Money,
  amountInDefaultCurrency: S10nAmount,
  billingPeriod: Option[BillingPeriod],
  nextPaymentDate: Option[LocalDate],
  firstPaymentDate: Option[FirstPaymentDate],
  paidInTotal: Option[Money],
  oneTime: Option[OneTimeSubscription],
  sendNotifications: Boolean,
  page: PageNumber
)
