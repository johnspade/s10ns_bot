package ru.johnspade.s10ns.user

import java.time.temporal.ChronoUnit

import doobie.util.meta.Meta
import org.joda.money.{CurrencyUnit, Money}
import org.postgresql.util.PGobject
import ru.johnspade.s10ns.bot.Dialog

object DoobieUserMeta {
  import cats.syntax.either._
  import io.circe.generic.extras.Configuration
  import io.circe.generic.extras.auto._
  import io.circe.parser._
  import io.circe.syntax._

  private implicit val jsonConfig: Configuration = Configuration.default.withDiscriminator("discriminator")

  implicit val dialogJsonMeta: Meta[Dialog] =
    Meta.Advanced
      .other[PGobject]("jsonb")
      .imap[Dialog](jsonStr => decode[Dialog](jsonStr.getValue).leftMap(err => throw err).merge)(
        dialog => {
          val o = new PGobject
          o.setType("jsonb")
          o.setValue(dialog.asJson.deepDropNullValues.noSpaces)
          o
        }
      )

  import io.circe.generic.auto._
  import io.circe.{Decoder, Encoder}

  implicit val CurrencyUnitEncoder: Encoder[CurrencyUnit] = Encoder.encodeString.contramap(_.getCode)
  implicit val CurrencyUnitDecoder: Decoder[CurrencyUnit] = Decoder.decodeString.emap { str =>
    Either.catchNonFatal(CurrencyUnit.of(str)).leftMap(_ => "CurrencyUnit")
  }

  implicit val ChronoUnitEncoder: Encoder[ChronoUnit] = Encoder.encodeString.contramap(_.name())
  implicit val ChronoUnitDecoder: Decoder[ChronoUnit] = Decoder.decodeString.emap { str =>
    Either.catchNonFatal(ChronoUnit.valueOf(str)).leftMap(_ => "BillingPeriodUnit")
  }

  implicit val MoneyEncoder: Encoder[Money] = Encoder.encodeString.contramap(_.toString)
  implicit val MoneyDecoder: Decoder[Money] = Decoder.decodeString.emap { s =>
    Either.catchNonFatal(Money.parse(s)).leftMap(_ => "Money")
  }

  implicit val currencyUnitMeta: Meta[CurrencyUnit] = Meta[String].timap(CurrencyUnit.of)(_.getCode)
}
