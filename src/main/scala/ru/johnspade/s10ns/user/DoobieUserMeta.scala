package ru.johnspade.s10ns.user

import java.time.temporal.ChronoUnit

import cats.syntax.either.*

import doobie.util.meta.Meta
import io.circe.Decoder
import io.circe.Encoder
import io.circe.derivation.Configuration
import io.circe.derivation.ConfiguredDecoder
import io.circe.derivation.ConfiguredEncoder
import io.circe.parser.*
import io.circe.syntax.*
import org.joda.money.CurrencyUnit
import org.joda.money.Money
import org.postgresql.util.PGobject

import ru.johnspade.s10ns.bot.Dialog
import ru.johnspade.s10ns.subscription.BillingPeriod
import ru.johnspade.s10ns.subscription.Subscription
import ru.johnspade.s10ns.subscription.SubscriptionDraft

object DoobieUserMeta {

  given Encoder[BillingPeriod] = Encoder.derived
  given Decoder[BillingPeriod] = Decoder.derived

  given Encoder[SubscriptionDraft] = Encoder.derived
  given Decoder[SubscriptionDraft] = Decoder.derived

  given Encoder[Subscription] = Encoder.derived
  given Decoder[Subscription] = Decoder.derived

  private val config    = Configuration.default.withDiscriminator("discriminator")
  given Encoder[Dialog] = ConfiguredEncoder.derived(using config)
  given Decoder[Dialog] = ConfiguredDecoder.derived(using config)

  implicit val dialogJsonMeta: Meta[Dialog] =
    Meta.Advanced
      .other[PGobject]("jsonb")
      .imap[Dialog](jsonStr => decode[Dialog](jsonStr.getValue).leftMap(err => throw err).merge)(dialog => {
        val o = new PGobject
        o.setType("jsonb")
        o.setValue(dialog.asJson.deepDropNullValues.noSpaces)
        o
      })

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
