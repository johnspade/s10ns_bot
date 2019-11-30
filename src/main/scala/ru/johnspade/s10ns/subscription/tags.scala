package ru.johnspade.s10ns.subscription

import java.time.LocalDate
import java.time.temporal.ChronoUnit

import ru.johnspade.s10ns.common.Tagged

object tags {
  object SubscriptionId extends Tagged[Long]
  type SubscriptionId = SubscriptionId.Type

  object SubscriptionName extends Tagged[String]
  type SubscriptionName = SubscriptionName.Type

  object SubscriptionAmount extends Tagged[Long]
  type SubscriptionAmount = SubscriptionAmount.Type

  object SubscriptionDescription extends Tagged[String]
  type SubscriptionDescription = SubscriptionDescription.Type

  object OneTimeSubscription extends Tagged[Boolean]
  type OneTimeSubscription = OneTimeSubscription.Type

  object BillingPeriodDuration extends Tagged[Int]
  type BillingPeriodDuration = BillingPeriodDuration.Type

  object BillingPeriodUnit extends Tagged[ChronoUnit]
  type BillingPeriodUnit = BillingPeriodUnit.Type

  object FirstPaymentDate extends Tagged[LocalDate]
  type FirstPaymentDate = FirstPaymentDate.Type

  object PageNumber extends Tagged[Int]
  type PageNumber = PageNumber.Type
}
