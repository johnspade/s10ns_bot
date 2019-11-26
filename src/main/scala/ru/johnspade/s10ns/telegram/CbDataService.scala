package ru.johnspade.s10ns.telegram

import cats.effect.Sync
import io.chrisdavenport.log4cats.Logger
import kantan.csv._
import kantan.csv.ops._

class CbDataService[F[_] : Sync : Logger] {
  import ru.johnspade.s10ns.telegram.CbDataService.cbDataDecoder

  def decode(csv: String): F[CbData] =
    csv.readCsv[List, CbData](CbData.codecs.csvConfig)
      .headOption
      .map {
        case Left(e) =>
          Sync[F].raiseError[CbData](e)
        case Right(value) =>
          Sync[F].pure(value)
      }
      .getOrElse(Sync[F].raiseError(new RuntimeException("Callback data is missing")))
}

object CbDataService {
  import ru.johnspade.s10ns.telegram.CbData.codecs._

  type Decode[A] = Seq[String] => DecodeResult[A]

  private def invalidDiscriminator[C](data: C): Decode[Nothing] =
    RowDecoder.from(_ => DecodeResult.typeError(s"Couldn't decode discriminator: $data")).decode

  def discriminatorRowDecoder[C: CellDecoder, A](index: Int)(discriminator: PartialFunction[C, Decode[A]]): RowDecoder[A] =
    RowDecoder.from(input => for {
      data <- input.lift(index).map(CellDecoder[C].decode).getOrElse(DecodeResult.outOfBounds(index))
      discriminated <- discriminator.applyOrElse(data, invalidDiscriminator)(input)
    } yield discriminated)

  implicit val cbDataDecoder: RowDecoder[CbData] = discriminatorRowDecoder[CbDataType, CbData](0) {
    case CbDataType.Subscriptions => RowDecoder[SubscriptionsCbData].decode
    case CbDataType.Subscription => RowDecoder[SubscriptionCbData].decode
    case CbDataType.BillingPeriodUnit => RowDecoder[BillingPeriodUnitCbData].decode
    case CbDataType.OneTime => RowDecoder[IsOneTimeCbData].decode
    case CbDataType.Ignore => RowDecoder[IgnoreCbData].decode
    case CbDataType.Calendar => RowDecoder[CalendarCbData].decode
    case CbDataType.FirstPaymentDate => RowDecoder[FirstPaymentDateCbData].decode
    case CbDataType.DefaultCurrency => RowDecoder[DefaultCurrencyCbData].decode
    case CbDataType.RemoveSubscription => RowDecoder[RemoveSubscriptionCbData].decode
    case CbDataType.EditS10n => RowDecoder[EditS10nCbData].decode
    case CbDataType.EditS10nName => RowDecoder[EditS10nNameCbData].decode
  }
}
