package ru.johnspade.s10ns.bot

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

import kantan.csv._
import kantan.csv.enumeratum._
import kantan.csv.java8._
import kantan.csv.ops._
import ru.johnspade.tgbot.callbackdata.named.MagnoliaRowEncoder._

import ru.johnspade.s10ns.bot.CbData._
import ru.johnspade.s10ns.subscription.BillingPeriodUnit

sealed abstract class CbData extends Product with Serializable {
  def toCsv: String = this.writeCsvRow(csvConfig)

  def print: String = ""
}

sealed trait StartsDialog extends CbData

sealed trait Skip extends CbData {
  override def print: String = "Skipped"
}

case object Ignore extends CbData

final case class S10ns(page: Int) extends CbData

final case class S10n(subscriptionId: Long, page: Int) extends CbData

final case class PeriodUnit(unit: BillingPeriodUnit) extends CbData {
  override def print: String = unit.chronoUnit.toString
}

case object SkipIsOneTime extends CbData with Skip

final case class OneTime(oneTime: Boolean) extends CbData {
  override def print: String = if (oneTime) "One time" else "Recurring"
}

case object EveryMonth extends CbData {
  override def print: String = "Every month"
}

final case class Calendar(date: LocalDate) extends CbData

final case class Years(yearMonth: YearMonth) extends CbData

final case class Months(year: Int) extends CbData

final case class FirstPayment(date: LocalDate) extends CbData {
  override def print: String = DateTimeFormatter.ISO_DATE.format(date)
}

case object DropFirstPayment extends CbData with Skip

final case class RemoveS10n(subscriptionId: Long, page: Int) extends CbData

final case class EditS10n(subscriptionId: Long, page: Int) extends CbData

final case class EditS10nName(subscriptionId: Long) extends CbData with StartsDialog

final case class EditS10nCurrency(subscriptionId: Long) extends CbData with StartsDialog

final case class EditS10nAmount(subscriptionId: Long) extends CbData with StartsDialog

final case class EditS10nOneTime(subscriptionId: Long) extends CbData with StartsDialog

final case class EditS10nBillingPeriod(subscriptionId: Long) extends CbData with StartsDialog

final case class EditS10nFirstPaymentDate(subscriptionId: Long) extends CbData with StartsDialog

case object DefCurrency extends CbData with StartsDialog

final case class S10nsPeriod(period: BillingPeriodUnit, page: Int) extends CbData

final case class Notify(subscriptionId: Long, enable: Boolean, page: Int) extends CbData

object CbData {
  val Separator: Char = '\u001D'

  val csvConfig: CsvConfiguration = rfc.withCellSeparator(Separator)

  implicit val yearMonthCellCodec: CellCodec[YearMonth] =
    CellCodec.from(s => DecodeResult(YearMonth.parse(s)))(ym => ym.toString)

  private def caseObjectRowCodec[T <: CbData](data: T): RowCodec[T] = RowCodec.from(_ => Right(data))(_ => Seq.empty)

  implicit val s10nsRowCodec: RowCodec[S10ns] =
    RowCodec.caseOrdered(S10ns.apply _)(S10ns.unapply)
  implicit val s10nRowCodec: RowCodec[S10n] =
    RowCodec.caseOrdered(S10n.apply _)(S10n.unapply)
  implicit val billingPeriodUnitRowCodec: RowCodec[PeriodUnit] =
    RowCodec.caseOrdered(PeriodUnit.apply _)(PeriodUnit.unapply)
  implicit val oneTimeRowCodec: RowCodec[OneTime] =
    RowCodec.caseOrdered(OneTime.apply _)(OneTime.unapply)
  implicit val calendarRowCodec: RowCodec[Calendar] =
    RowCodec.caseOrdered(Calendar.apply _)(Calendar.unapply)
  implicit val yearsRowCodec: RowCodec[Years] =
    RowCodec.caseOrdered(Years.apply _)(Years.unapply)
  implicit val monthsRowCodec: RowCodec[Months] =
    RowCodec.caseOrdered(Months.apply _)(Months.unapply)
  implicit val firstPaymentDateRowCodec: RowCodec[FirstPayment] =
    RowCodec.caseOrdered(FirstPayment.apply _)(FirstPayment.unapply)
  implicit val removeSubscriptionRowCodec: RowCodec[RemoveS10n] =
    RowCodec.caseOrdered(RemoveS10n.apply _)(RemoveS10n.unapply)
  implicit val editS10nRowCodec: RowCodec[EditS10n] =
    RowCodec.caseOrdered(EditS10n.apply _)(EditS10n.unapply)
  implicit val editS10nNameRowCodec: RowCodec[EditS10nName] =
    RowCodec.caseOrdered(EditS10nName.apply _)(EditS10nName.unapply)
  implicit val editS10nCurrencyRowCodec: RowCodec[EditS10nCurrency] =
    RowCodec.caseOrdered(EditS10nCurrency.apply _)(EditS10nCurrency.unapply)
  implicit val editS10nAmountRowCodec: RowCodec[EditS10nAmount] =
    RowCodec.caseOrdered(EditS10nAmount.apply _)(EditS10nAmount.unapply)
  implicit val editS10nOneTimeRowCodec: RowCodec[EditS10nOneTime] =
    RowCodec.caseOrdered(EditS10nOneTime.apply _)(EditS10nOneTime.unapply)
  implicit val editS10nBillingPeriodRowCodec: RowCodec[EditS10nBillingPeriod] =
    RowCodec.caseOrdered(EditS10nBillingPeriod.apply _)(EditS10nBillingPeriod.unapply)
  implicit val editS10nFirstPaymentDateRowCodec: RowCodec[EditS10nFirstPaymentDate] =
    RowCodec.caseOrdered(EditS10nFirstPaymentDate.apply _)(EditS10nFirstPaymentDate.unapply)
  implicit val s10nsPeriodRowCodec: RowCodec[S10nsPeriod] =
    RowCodec.caseOrdered(S10nsPeriod.apply _)(S10nsPeriod.unapply)
  implicit val notifyRowCodec: RowCodec[Notify] =
    RowCodec.caseOrdered(Notify.apply _)(Notify.unapply)
  implicit val skipIsOneTimeRowCodec: RowCodec[SkipIsOneTime.type] =
    caseObjectRowCodec(SkipIsOneTime)
  implicit val dropFirstPaymentRowCodec: RowCodec[DropFirstPayment.type] =
    caseObjectRowCodec(DropFirstPayment)
  implicit val ignoreRowCodec: RowCodec[Ignore.type] =
    caseObjectRowCodec(Ignore)
  implicit val defCurrencyRowCodec: RowCodec[DefCurrency.type] =
    caseObjectRowCodec(DefCurrency)
  implicit val everyMonthRowCodec: RowCodec[EveryMonth.type] =
    caseObjectRowCodec(EveryMonth)
}
