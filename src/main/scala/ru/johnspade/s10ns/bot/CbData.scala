package ru.johnspade.s10ns.bot

import java.time.LocalDate

import kantan.csv._
import kantan.csv.java8._
import kantan.csv.ops._
import ru.johnspade.s10ns.bot.CbData._
import ru.johnspade.s10ns.csv.MagnoliaRowEncoder._
import ru.johnspade.s10ns.subscription.BillingPeriodUnit
import ru.johnspade.s10ns.subscription.tags._
import supertagged.{@@, lifterF}
import kantan.csv.enumeratum._

sealed abstract class CbData extends Product with Serializable {
  def toCsv: String = this.writeCsvRow(csvConfig)
}

case object Ignore extends CbData
final case class S10ns(page: PageNumber) extends CbData
final case class S10n(subscriptionId: SubscriptionId, page: PageNumber) extends CbData
final case class PeriodUnit(unit: BillingPeriodUnit) extends CbData
case object SkipIsOneTime extends CbData
final case class OneTime(oneTime: OneTimeSubscription) extends CbData
final case class Calendar(date: LocalDate) extends CbData
final case class FirstPayment(date: FirstPaymentDate) extends CbData
case object DropFirstPayment extends CbData
final case class RemoveS10n(subscriptionId: SubscriptionId, page: PageNumber) extends CbData
final case class EditS10n(subscriptionId: SubscriptionId, page: PageNumber) extends CbData
final case class EditS10nName(subscriptionId: SubscriptionId) extends CbData
final case class EditS10nCurrency(subscriptionId: SubscriptionId) extends CbData
final case class EditS10nAmount(subscriptionId: SubscriptionId) extends CbData
final case class EditS10nOneTime(subscriptionId: SubscriptionId) extends CbData
final case class EditS10nBillingPeriod(subscriptionId: SubscriptionId) extends CbData
final case class EditS10nFirstPaymentDate(subscriptionId: SubscriptionId) extends CbData
case object DefCurrency extends CbData

object CbData {
  val Separator: Char = '\u001D'

  val csvConfig: CsvConfiguration = rfc.withCellSeparator(Separator)

  implicit def liftedCellEncoder[T, U](implicit cellEncoder: CellEncoder[T]): CellEncoder[T @@ U] =
    lifterF[CellEncoder].lift
  implicit def liftedCellDecoder[T, U](implicit cellDecoder: CellDecoder[T]): CellDecoder[T @@ U] =
    lifterF[CellDecoder].lift

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
  implicit val skipIsOneTimeRowCodec: RowCodec[SkipIsOneTime.type] =
    caseObjectRowCodec(SkipIsOneTime)
  implicit val dropFirstPaymentRowCodec: RowCodec[DropFirstPayment.type] =
    caseObjectRowCodec(DropFirstPayment)
  implicit val ignoreRowCodec: RowCodec[Ignore.type] =
    caseObjectRowCodec(Ignore)
  implicit val defCurrencyRowCodec: RowCodec[DefCurrency.type] =
    caseObjectRowCodec(DefCurrency)
}
