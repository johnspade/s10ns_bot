package ru.johnspade.s10ns.telegram

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

import ru.johnspade.s10ns.common.PageNumber
import ru.johnspade.s10ns.subscription.{BillingPeriodUnit, OneTimeSubscription, SubscriptionId}

sealed trait CallbackData {
  def `type`: CallbackDataType

  /**
   * Converts the data to string (without type)
   */
  def toDataString: String

  final def toDataStringWithTag = s"${`type`.hex}\u001D$toDataString"
}

object CallbackData {
  def subscriptions(page: PageNumber): String =
    SubscriptionsCallbackData(page).toDataStringWithTag

  def subscription(subscriptionId: SubscriptionId, page: PageNumber): String =
    SubscriptionCallbackData(subscriptionId, page).toDataStringWithTag

  def billingPeriodUnit(unit: BillingPeriodUnit): String = BillingPeriodUnitCallbackData(unit).toDataStringWithTag

  def oneTime(oneTime: OneTimeSubscription): String = IsOneTimeCallbackData(oneTime).toDataStringWithTag

  def ignore: String = IgnoreCallbackData().toDataStringWithTag

  def calendar(date: LocalDate): String = CalendarCallbackData(date).toDataStringWithTag

  def dayOfMonth(date: LocalDate): String = FirstPaymentDateCallbackData(date).toDataStringWithTag

  val defaultCurrency: String = DefaultCurrencyCallbackData().toDataStringWithTag

  def removeSubscription(subscriptionId: SubscriptionId, page: PageNumber): String =
    RemoveSubscriptionCallbackData(subscriptionId, page).toDataStringWithTag
}

case class SubscriptionsCallbackData(page: PageNumber) extends CallbackData {
  override val `type`: CallbackDataType = CallbackDataType.Subscriptions

  override def toDataString: String = page.value.toString
}

object SubscriptionsCallbackData {
  def fromString(s: String): SubscriptionsCallbackData =
    SubscriptionsCallbackData(PageNumber(java.lang.Long.decode(s).toInt))
}

case class SubscriptionCallbackData(
  subscriptionId: SubscriptionId,
  page: PageNumber
) extends CallbackData {
  override val `type`: CallbackDataType = CallbackDataType.Subscription

  override def toDataString: String = s"${subscriptionId.value}\u001D${page.value}"
}

object SubscriptionCallbackData {
  def fromString(s: String): SubscriptionCallbackData = {
    val parts = s.split('\u001D')
    SubscriptionCallbackData(
      subscriptionId = SubscriptionId(java.lang.Long.decode(parts(0))),
      page = PageNumber(java.lang.Long.decode(parts(1)).toInt)
    )
  }
}

case class BillingPeriodUnitCallbackData(
  unit: BillingPeriodUnit
) extends CallbackData {
  override def `type`: CallbackDataType = CallbackDataType.BillingPeriodUnit

  override def toDataString: String = unit.value.name()
}

object BillingPeriodUnitCallbackData {
  def fromString(s: String): BillingPeriodUnitCallbackData =
    BillingPeriodUnitCallbackData(unit = BillingPeriodUnit(ChronoUnit.valueOf(s)))
}

case class IsOneTimeCallbackData(
  oneTime: OneTimeSubscription
) extends CallbackData {
  override val `type`: CallbackDataType = CallbackDataType.OneTime

  override def toDataString: String =
    if (oneTime.value) "1"
    else "0"
}

object IsOneTimeCallbackData {
  def fromString(s: String): IsOneTimeCallbackData = IsOneTimeCallbackData(oneTime = OneTimeSubscription(s == "1"))
}

case class IgnoreCallbackData() extends CallbackData {
  override def `type`: CallbackDataType = CallbackDataType.Ignore

  override def toDataString: String = ""
}

case class CalendarCallbackData(
  date: LocalDate
) extends CallbackData {
  override def `type`: CallbackDataType = CallbackDataType.Calendar

  override def toDataString: String = DateTimeFormatter.ISO_DATE.format(date)
}

object CalendarCallbackData {
  def fromString(s: String): CalendarCallbackData = CalendarCallbackData(LocalDate.parse(s, DateTimeFormatter.ISO_DATE))
}

case class FirstPaymentDateCallbackData(
  date: LocalDate
) extends CallbackData {
  override def `type`: CallbackDataType = CallbackDataType.FirstPaymentDate

  override def toDataString: String = DateTimeFormatter.ISO_DATE.format(date)
}

object FirstPaymentDateCallbackData {
  def fromString(s: String): FirstPaymentDateCallbackData =
    FirstPaymentDateCallbackData(LocalDate.parse(s, DateTimeFormatter.ISO_DATE))
}

case class DefaultCurrencyCallbackData() extends CallbackData {
  override def `type`: CallbackDataType = CallbackDataType.DefaultCurrency
  override def toDataString: String = ""
}

case class RemoveSubscriptionCallbackData(
  subscriptionId: SubscriptionId,
  page: PageNumber
) extends CallbackData {
  override def `type`: CallbackDataType = CallbackDataType.RemoveSubscription
  override def toDataString: String = s"${subscriptionId.value}\u001D${page.value}"
}

object RemoveSubscriptionCallbackData {
  def fromString(s: String): RemoveSubscriptionCallbackData = {
    val parts = s.split('\u001D')
    RemoveSubscriptionCallbackData(
      subscriptionId = SubscriptionId(java.lang.Long.decode(parts(0))),
      page = PageNumber(java.lang.Long.decode(parts(1)).toInt)
    )
  }
}
