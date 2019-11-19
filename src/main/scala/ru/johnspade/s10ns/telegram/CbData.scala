package ru.johnspade.s10ns.telegram

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

import ru.johnspade.s10ns.common.PageNumber
import ru.johnspade.s10ns.subscription.{BillingPeriodUnit, OneTimeSubscription, SubscriptionId}

sealed trait CbData {
  def `type`: CbDataType

  /**
    * Converts the data to string (without type)
    */
  def toDataString: String

  final def toDataStringWithTag = s"${`type`.hex}\u001D$toDataString"
}

object CbData {
  def subscriptions(page: PageNumber): String =
    SubscriptionsCbData(page).toDataStringWithTag

  def subscription(subscriptionId: SubscriptionId, page: PageNumber): String =
    SubscriptionCbData(subscriptionId, page).toDataStringWithTag

  def billingPeriodUnit(unit: BillingPeriodUnit): String = BillingPeriodUnitCbData(unit).toDataStringWithTag

  def oneTime(oneTime: OneTimeSubscription): String = IsOneTimeCbData(oneTime).toDataStringWithTag

  def ignore: String = IgnoreCbData().toDataStringWithTag

  def calendar(date: LocalDate): String = CalendarCbData(date).toDataStringWithTag

  def dayOfMonth(date: LocalDate): String = FirstPaymentDateCbData(date).toDataStringWithTag

  val defaultCurrency: String = DefaultCurrencyCbData().toDataStringWithTag

  def removeSubscription(subscriptionId: SubscriptionId, page: PageNumber): String =
    RemoveSubscriptionCbData(subscriptionId, page).toDataStringWithTag

  def editS10n(subscriptionId: SubscriptionId, page: PageNumber): String =
    EditS10nCbData(subscriptionId, page).toDataStringWithTag

  def editS10nName(subscriptionId: SubscriptionId): String = EditS10nNameCbData(subscriptionId).toDataStringWithTag
}

case class SubscriptionsCbData(page: PageNumber) extends CbData {
  override val `type`: CbDataType = CbDataType.Subscriptions

  override def toDataString: String = page.value.toString
}

object SubscriptionsCbData {
  def fromString(s: String): SubscriptionsCbData =
    SubscriptionsCbData(PageNumber(java.lang.Long.decode(s).toInt))
}

case class SubscriptionCbData(
  subscriptionId: SubscriptionId,
  page: PageNumber
) extends CbData {
  override val `type`: CbDataType = CbDataType.Subscription

  override def toDataString: String = s"${subscriptionId.value}\u001D${page.value}"
}

object SubscriptionCbData {
  def fromString(s: String): SubscriptionCbData = {
    val parts = s.split('\u001D')
    SubscriptionCbData(
      subscriptionId = SubscriptionId(java.lang.Long.decode(parts(0))),
      page = PageNumber(java.lang.Long.decode(parts(1)).toInt)
    )
  }
}

case class BillingPeriodUnitCbData(
  unit: BillingPeriodUnit
) extends CbData {
  override def `type`: CbDataType = CbDataType.BillingPeriodUnit

  override def toDataString: String = unit.value.name()
}

object BillingPeriodUnitCbData {
  def fromString(s: String): BillingPeriodUnitCbData =
    BillingPeriodUnitCbData(unit = BillingPeriodUnit(ChronoUnit.valueOf(s)))
}

case class IsOneTimeCbData(
  oneTime: OneTimeSubscription
) extends CbData {
  override val `type`: CbDataType = CbDataType.OneTime

  override def toDataString: String =
    if (oneTime.value) "1"
    else "0"
}

object IsOneTimeCbData {
  def fromString(s: String): IsOneTimeCbData = IsOneTimeCbData(oneTime = OneTimeSubscription(s == "1"))
}

case class IgnoreCbData() extends CbData {
  override def `type`: CbDataType = CbDataType.Ignore

  override def toDataString: String = ""
}

case class CalendarCbData(
  date: LocalDate
) extends CbData {
  override def `type`: CbDataType = CbDataType.Calendar

  override def toDataString: String = DateTimeFormatter.ISO_DATE.format(date)
}

object CalendarCbData {
  def fromString(s: String): CalendarCbData = CalendarCbData(LocalDate.parse(s, DateTimeFormatter.ISO_DATE))
}

case class FirstPaymentDateCbData(
  date: LocalDate
) extends CbData {
  override def `type`: CbDataType = CbDataType.FirstPaymentDate

  override def toDataString: String = DateTimeFormatter.ISO_DATE.format(date)
}

object FirstPaymentDateCbData {
  def fromString(s: String): FirstPaymentDateCbData =
    FirstPaymentDateCbData(LocalDate.parse(s, DateTimeFormatter.ISO_DATE))
}

case class DefaultCurrencyCbData() extends CbData {
  override def `type`: CbDataType = CbDataType.DefaultCurrency
  override def toDataString: String = ""
}

case class RemoveSubscriptionCbData(
  subscriptionId: SubscriptionId,
  page: PageNumber
) extends CbData {
  override def `type`: CbDataType = CbDataType.RemoveSubscription
  override def toDataString: String = s"${subscriptionId.value}\u001D${page.value}"
}

object RemoveSubscriptionCbData {
  def fromString(s: String): RemoveSubscriptionCbData = {
    val parts = s.split('\u001D')
    RemoveSubscriptionCbData(
      subscriptionId = SubscriptionId(java.lang.Long.decode(parts(0))),
      page = PageNumber(java.lang.Long.decode(parts(1)).toInt)
    )
  }
}

case class EditS10nCbData(
  subscriptionId: SubscriptionId,
  page: PageNumber
) extends CbData {
  override def `type`: CbDataType = CbDataType.EditS10n
  override def toDataString: String = s"${subscriptionId.value}\u001D${page.value}"
}

object EditS10nCbData {
  def fromString(s: String): EditS10nCbData = {
    val parts = s.split('\u001D')
    EditS10nCbData(
      subscriptionId = SubscriptionId(java.lang.Long.decode(parts(0))),
      page = PageNumber(java.lang.Long.decode(parts(1)).toInt)
    )
  }
}

case class EditS10nNameCbData(
  subscriptionId: SubscriptionId
) extends CbData {
  override def `type`: CbDataType = CbDataType.EditS10nName
  override def toDataString: String = subscriptionId.value.toString
}

object EditS10nNameCbData {
  def fromString(s: String): EditS10nNameCbData =
    EditS10nNameCbData(
      subscriptionId = SubscriptionId(java.lang.Long.decode(s))
    )
}
