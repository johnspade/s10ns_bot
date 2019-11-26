package ru.johnspade.s10ns.telegram

import java.time.LocalDate
import java.time.temporal.ChronoUnit

import kantan.csv._
import kantan.csv.java8._
import kantan.csv.ops._
import ru.johnspade.s10ns.common.tags._
import ru.johnspade.s10ns.subscription.tags._
import ru.johnspade.s10ns.telegram.CbData.codecs._

sealed trait CbData {
  def `type`: CbDataType
}

object CbData {
  def subscriptions(page: PageNumber): String = SubscriptionsCbData(page = page).writeCsvRow(csvConfig)

  def subscription(subscriptionId: SubscriptionId, page: PageNumber): String =
    SubscriptionCbData(subscriptionId = subscriptionId, page = page).writeCsvRow(csvConfig)

  def billingPeriodUnit(unit: BillingPeriodUnit): String = BillingPeriodUnitCbData(unit = unit).writeCsvRow(csvConfig)

  def oneTime(oneTime: OneTimeSubscription): String = IsOneTimeCbData(oneTime = oneTime).writeCsvRow(csvConfig)

  val ignore: String = IgnoreCbData().writeCsvRow(csvConfig)

  def calendar(date: LocalDate): String = CalendarCbData(date = date).writeCsvRow(csvConfig)

  def dayOfMonth(date: FirstPaymentDate): String = FirstPaymentDateCbData(date = date).writeCsvRow(csvConfig)

  val defaultCurrency: String = DefaultCurrencyCbData().writeCsvRow(csvConfig)

  def removeSubscription(subscriptionId: SubscriptionId, page: PageNumber): String =
    RemoveSubscriptionCbData(subscriptionId = subscriptionId, page = page).writeCsvRow(csvConfig)

  def editS10n(subscriptionId: SubscriptionId, page: PageNumber): String =
    EditS10nCbData(subscriptionId = subscriptionId, page = page).writeCsvRow(csvConfig)

  def editS10nName(subscriptionId: SubscriptionId): String =
    EditS10nNameCbData(subscriptionId = subscriptionId).writeCsvRow(csvConfig)

  object codecs {
    val csvConfig: CsvConfiguration = rfc.withCellSeparator('\u001D')

    implicit val cbDataTypeCellCodec: CellCodec[CbDataType] =
      CellCodec.from(s => DecodeResult(CbDataType.withValue(Integer.parseInt(s, 16))))(_.hex)
    implicit val chronoUnitCellCodec: CellCodec[ChronoUnit] =
      CellCodec.from(s => DecodeResult(ChronoUnit.valueOf(s)))(u => u.name())

    implicit val s10nsCbDataRowCodec: RowCodec[SubscriptionsCbData] =
      RowCodec.caseOrdered(SubscriptionsCbData.apply _)(SubscriptionsCbData.unapply)
    implicit val s10nCbDataRowCodec: RowCodec[SubscriptionCbData] =
      RowCodec.caseOrdered(SubscriptionCbData.apply _)(SubscriptionCbData.unapply)
    implicit val billingPeriodUnitCbDataRowCodec: RowCodec[BillingPeriodUnitCbData] =
      RowCodec.caseOrdered(BillingPeriodUnitCbData.apply _)(BillingPeriodUnitCbData.unapply)
    implicit val oneTimeCbDataRowCodec: RowCodec[IsOneTimeCbData] =
      RowCodec.caseOrdered(IsOneTimeCbData.apply _)(IsOneTimeCbData.unapply)
    implicit val calendarCbDataRowCodec: RowCodec[CalendarCbData] =
      RowCodec.caseOrdered(CalendarCbData.apply _)(CalendarCbData.unapply)
    implicit val firstPaymentDateCbDataRowCodec: RowCodec[FirstPaymentDateCbData] =
      RowCodec.caseOrdered(FirstPaymentDateCbData.apply _)(FirstPaymentDateCbData.unapply)
    implicit val removeSubscriptionCbDataRowCodec: RowCodec[RemoveSubscriptionCbData] =
      RowCodec.caseOrdered(RemoveSubscriptionCbData.apply _)(RemoveSubscriptionCbData.unapply)
    implicit val editS10nCbDataRowCodec: RowCodec[EditS10nCbData] =
      RowCodec.caseOrdered(EditS10nCbData.apply _)(EditS10nCbData.unapply)
    implicit val editS10nNameCbDataRowCodec: RowCodec[EditS10nNameCbData] =
      RowCodec.caseOrdered(EditS10nNameCbData.apply _)(EditS10nNameCbData.unapply)
    implicit val ignoreCbDataRowCodec: RowCodec[IgnoreCbData] =
      RowCodec.caseOrdered(IgnoreCbData.apply _)(IgnoreCbData.unapply)
    implicit val defaultCurrencyCbDataCodec: RowCodec[DefaultCurrencyCbData] =
      RowCodec.caseOrdered(DefaultCurrencyCbData.apply _)(DefaultCurrencyCbData.unapply)
    implicit val ignoreCbDataRowEncoder: RowEncoder[IgnoreCbData] = implicitly[RowEncoder[IgnoreCbData]]
  }
}

case class IgnoreCbData(
  override val `type`: CbDataType = CbDataType.Ignore
) extends CbData

case class SubscriptionsCbData(
  override val `type`: CbDataType = CbDataType.Subscriptions,
  page: PageNumber
) extends CbData

case class SubscriptionCbData(
  override val `type`: CbDataType = CbDataType.Subscription,
  subscriptionId: SubscriptionId,
  page: PageNumber
) extends CbData

case class BillingPeriodUnitCbData(
  override val `type`: CbDataType = CbDataType.BillingPeriodUnit,
  unit: BillingPeriodUnit
) extends CbData

case class IsOneTimeCbData(
  override val `type`: CbDataType = CbDataType.OneTime,
  oneTime: OneTimeSubscription
) extends CbData

case class CalendarCbData(
  override val `type`: CbDataType = CbDataType.Calendar,
  date: LocalDate
) extends CbData

case class FirstPaymentDateCbData(
  override val `type`: CbDataType = CbDataType.FirstPaymentDate,
  date: FirstPaymentDate
) extends CbData

case class RemoveSubscriptionCbData(
  override val `type`: CbDataType = CbDataType.RemoveSubscription,
  subscriptionId: SubscriptionId,
  page: PageNumber
) extends CbData

case class EditS10nCbData(
  override val `type`: CbDataType = CbDataType.EditS10n,
  subscriptionId: SubscriptionId,
  page: PageNumber
) extends CbData

case class EditS10nNameCbData(
  override val `type`: CbDataType = CbDataType.EditS10nName,
  subscriptionId: SubscriptionId
) extends CbData

case class DefaultCurrencyCbData(
  override val `type`: CbDataType = CbDataType.DefaultCurrency
) extends CbData
