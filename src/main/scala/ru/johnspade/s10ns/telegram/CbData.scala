package ru.johnspade.s10ns.telegram

import java.time.LocalDate
import java.time.temporal.ChronoUnit

import cats.syntax.option._
import kantan.csv._
import kantan.csv.java8._
import kantan.csv.ops._
import ru.johnspade.s10ns.csv.MagnoliaRowEncoder._
import ru.johnspade.s10ns.subscription.tags._
import ru.johnspade.s10ns.telegram.CbData._
import supertagged.{@@, lifterF}

sealed abstract class CbData extends Product with Serializable {
  def toCsv: Option[String] = this.writeCsvRow(csvConfig).some
}

case object Ignore extends CbData
final case class S10ns(page: PageNumber) extends CbData
final case class S10n(subscriptionId: SubscriptionId, page: PageNumber) extends CbData
final case class PeriodUnit(unit: BillingPeriodUnit) extends CbData
final case class OneTime(oneTime: OneTimeSubscription) extends CbData
final case class Calendar(date: LocalDate) extends CbData
final case class FirstPayment(date: FirstPaymentDate) extends CbData
final case class RemoveS10n(subscriptionId: SubscriptionId, page: PageNumber) extends CbData
final case class EditS10n(subscriptionId: SubscriptionId, page: PageNumber) extends CbData
final case class EditS10nName(subscriptionId: SubscriptionId) extends CbData
case object DefCurrency extends CbData

object CbData {
  val csvConfig: CsvConfiguration = rfc.withCellSeparator('\u001D')

  implicit def liftedCellEncoder[T, U](implicit cellEncoder: CellEncoder[T]): CellEncoder[T @@ U] =
    lifterF[CellEncoder].lift
  implicit def liftedCellDecoder[T, U](implicit cellDecoder: CellDecoder[T]): CellDecoder[T @@ U] =
    lifterF[CellDecoder].lift

  implicit val chronoUnitCellCodec: CellCodec[ChronoUnit] =
    CellCodec.from(s => DecodeResult(ChronoUnit.valueOf(s)))(_.name)

  private def caseObjectRowCodec[T <: CbData](data: T): RowCodec[T] = RowCodec.from(_ => Right(data))(_ => Seq.empty)

  implicit val s10nsCbDataRowCodec: RowCodec[S10ns] =
    RowCodec.caseOrdered(S10ns.apply _)(S10ns.unapply)
  implicit val s10nCbDataRowCodec: RowCodec[S10n] =
    RowCodec.caseOrdered(S10n.apply _)(S10n.unapply)
  implicit val billingPeriodUnitCbDataRowCodec: RowCodec[PeriodUnit] =
    RowCodec.caseOrdered(PeriodUnit.apply _)(PeriodUnit.unapply)
  implicit val oneTimeCbDataRowCodec: RowCodec[OneTime] =
    RowCodec.caseOrdered(OneTime.apply _)(OneTime.unapply)
  implicit val calendarCbDataRowCodec: RowCodec[Calendar] =
    RowCodec.caseOrdered(Calendar.apply _)(Calendar.unapply)
  implicit val firstPaymentDateCbDataRowCodec: RowCodec[FirstPayment] =
    RowCodec.caseOrdered(FirstPayment.apply _)(FirstPayment.unapply)
  implicit val removeSubscriptionCbDataRowCodec: RowCodec[RemoveS10n] =
    RowCodec.caseOrdered(RemoveS10n.apply _)(RemoveS10n.unapply)
  implicit val editS10nCbDataRowCodec: RowCodec[EditS10n] =
    RowCodec.caseOrdered(EditS10n.apply _)(EditS10n.unapply)
  implicit val editS10nNameCbDataRowCodec: RowCodec[EditS10nName] =
    RowCodec.caseOrdered(EditS10nName.apply _)(EditS10nName.unapply)
  implicit val ignoreRowCodec: RowCodec[Ignore.type] =
    caseObjectRowCodec(Ignore)
  implicit val defCurrencyRowCodec: RowCodec[DefCurrency.type] =
    caseObjectRowCodec(DefCurrency)
}
