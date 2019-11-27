package ru.johnspade.s10ns.telegram

import java.time.LocalDate
import java.time.temporal.ChronoUnit

import kantan.csv._
import kantan.csv.java8._
import kantan.csv.ops._
import ru.johnspade.s10ns.common.tags._
import ru.johnspade.s10ns.subscription.tags._
import ru.johnspade.s10ns.telegram.CbData.codecs._
import supertagged.TaggedType

sealed trait CbData {
  def `type`: CbDataType
}

object CbData {
  object CbDataCsv extends TaggedType[String]
  type CbDataCsv = CbDataCsv.Type

  def subscriptions(page: PageNumber): CbDataCsv = writeCsv(SubscriptionsCbData(page = page))

  def subscription(subscriptionId: SubscriptionId, page: PageNumber): CbDataCsv =
    writeCsv(SubscriptionCbData(subscriptionId = subscriptionId, page = page))

  def billingPeriodUnit(unit: BillingPeriodUnit): CbDataCsv = writeCsv(BillingPeriodUnitCbData(unit = unit))

  def oneTime(oneTime: OneTimeSubscription): CbDataCsv = writeCsv(IsOneTimeCbData(oneTime = oneTime))

  val ignore: CbDataCsv = writeCsv(IgnoreCbData())

  def calendar(date: LocalDate): CbDataCsv = writeCsv(CalendarCbData(date = date))

  def dayOfMonth(date: FirstPaymentDate): CbDataCsv = writeCsv(FirstPaymentDateCbData(date = date))

  val defaultCurrency: CbDataCsv = writeCsv(DefaultCurrencyCbData())

  def removeSubscription(subscriptionId: SubscriptionId, page: PageNumber): CbDataCsv =
    writeCsv(RemoveSubscriptionCbData(subscriptionId = subscriptionId, page = page))

  def editS10n(subscriptionId: SubscriptionId, page: PageNumber): CbDataCsv =
    writeCsv(EditS10nCbData(subscriptionId = subscriptionId, page = page))

  def editS10nName(subscriptionId: SubscriptionId): CbDataCsv =
    writeCsv(EditS10nNameCbData(subscriptionId = subscriptionId))

  private def writeCsv[A <: CbData](data: A)(implicit encoder: RowEncoder[A]): CbDataCsv =
    CbDataCsv @@ s"${CellEncoder[CbDataType].encode(data.`type`)}${csvConfig.cellSeparator}${data.writeCsvRow(csvConfig)}"

  type Decode[A] = Seq[String] => DecodeResult[A]

  private def invalidDiscriminator[C](data: C): Decode[Nothing] =
    RowDecoder.from(_ => DecodeResult.typeError(s"Couldn't decode discriminator: $data")).decode

  def discriminatorRowDecoder[C: CellDecoder, A](discriminator: PartialFunction[C, Decode[A]]): RowDecoder[A] =
    RowDecoder.from(input => for {
      data <- input.headOption.map(CellDecoder[C].decode).getOrElse(DecodeResult.outOfBounds(0))
      discriminated <- discriminator.applyOrElse(data, invalidDiscriminator)(input.tail)
    } yield discriminated)

  implicit val cbDataDecoder: RowDecoder[CbData] = discriminatorRowDecoder[CbDataType, CbData] {
    case CbDataType.Subscriptions => decode[SubscriptionsCbData]
    case CbDataType.Subscription => decode[SubscriptionCbData]
    case CbDataType.BillingPeriodUnit => decode[BillingPeriodUnitCbData]
    case CbDataType.OneTime => decode[IsOneTimeCbData]
    case CbDataType.Ignore => decode[IgnoreCbData]
    case CbDataType.Calendar => decode[CalendarCbData]
    case CbDataType.FirstPaymentDate => decode[FirstPaymentDateCbData]
    case CbDataType.DefaultCurrency => decode[DefaultCurrencyCbData]
    case CbDataType.RemoveSubscription => decode[RemoveSubscriptionCbData]
    case CbDataType.EditS10n => decode[EditS10nCbData]
    case CbDataType.EditS10nName => decode[EditS10nNameCbData]
  }

  private def decode[A](implicit decoder: RowDecoder[A]): Decode[A] = RowDecoder.apply[A].decode _

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

final case class IgnoreCbData(
  ignore: String = ""
) extends CbData {
  override val `type`: CbDataType = CbDataType.Ignore
}

final case class SubscriptionsCbData(
  page: PageNumber
) extends CbData {
  override val `type`: CbDataType = CbDataType.Subscriptions
}

final case class SubscriptionCbData(
  subscriptionId: SubscriptionId,
  page: PageNumber
) extends CbData {
  override val `type`: CbDataType = CbDataType.Subscription
}

final case class BillingPeriodUnitCbData(
  unit: BillingPeriodUnit
) extends CbData {
  override val `type`: CbDataType = CbDataType.BillingPeriodUnit
}

final case class IsOneTimeCbData(
  oneTime: OneTimeSubscription
) extends CbData {
  override val `type`: CbDataType = CbDataType.OneTime
}

final case class CalendarCbData(
  override val `type`: CbDataType = CbDataType.Calendar,
  date: LocalDate
) extends CbData

final case class FirstPaymentDateCbData(
  date: FirstPaymentDate
) extends CbData {
  override val `type`: CbDataType = CbDataType.FirstPaymentDate
}

final case class RemoveSubscriptionCbData(
  subscriptionId: SubscriptionId,
  page: PageNumber
) extends CbData {
  override val `type`: CbDataType = CbDataType.RemoveSubscription
}

final case class EditS10nCbData(
  subscriptionId: SubscriptionId,
  page: PageNumber
) extends CbData {
  override val `type`: CbDataType = CbDataType.EditS10n
}

final case class EditS10nNameCbData(
  subscriptionId: SubscriptionId
) extends CbData {
  override val `type`: CbDataType = CbDataType.EditS10nName
}

final case class DefaultCurrencyCbData(
  ignore: String = ""
) extends CbData {
  override val `type`: CbDataType = CbDataType.DefaultCurrency
}
