package ru.johnspade.s10ns.bot

import java.time.temporal.ChronoUnit

import cats.effect.IO
import org.joda.money.{CurrencyUnit, Money}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ru.johnspade.s10ns.exchangerates.ExchangeRatesStorage
import cats.implicits._
import ru.johnspade.s10ns.subscription.{BillingPeriod, Subscription}
import ru.johnspade.s10ns.subscription.tags.{BillingPeriodDuration, BillingPeriodUnit, SubscriptionId, SubscriptionName}
import ru.johnspade.s10ns.user.tags.UserId

class MoneyServiceSpec extends AnyFlatSpec with Matchers {
  private val exchangeRatesStorage: ExchangeRatesStorage[IO] = new ExchangeRatesStorage[IO] {
    override val getRates: IO[Map[String, BigDecimal]] = IO(Map(
      "EUR" -> BigDecimal(1),
      "RUB" -> BigDecimal(69.479142),
      "USD" -> BigDecimal(1.116732),
      "RWF" -> BigDecimal(1055.766139)
    ))
  }
  private val moneyService = new MoneyService[IO](exchangeRatesStorage)

  private val s10n1 = Subscription(
    SubscriptionId(1L),
    UserId(0L),
    SubscriptionName("s10n1"),
    Money.of(CurrencyUnit.of("RUB"), 256),
    None,
    BillingPeriod(BillingPeriodDuration(1), BillingPeriodUnit(ChronoUnit.MONTHS)).some,
    None
  )
  private val s10n2 = Subscription(
    SubscriptionId(2L),
    UserId(0L),
    SubscriptionName("s10n2"),
    Money.of(CurrencyUnit.of("RUB"), 300),
    None,
    BillingPeriod(BillingPeriodDuration(1), BillingPeriodUnit(ChronoUnit.MONTHS)).some,
    None
  )

  "convert" should "convert between currencies correctly" in {
    val amountRub = Money.of(CurrencyUnit.of("RUB"), 1598)
    moneyService.convert(amountRub, CurrencyUnit.USD).unsafeRunSync shouldBe
      Money.of(CurrencyUnit.USD, BigDecimal(25.68).bigDecimal).some

    val amountUsd = Money.of(CurrencyUnit.USD, 15)
    moneyService.convert(amountUsd, CurrencyUnit.EUR).unsafeRunSync shouldBe
      Money.of(CurrencyUnit.EUR, BigDecimal(13.43).bigDecimal).some
  }

  "sum" should "sum subscriptions with a default currency" in {
    moneyService.sum(List(s10n1, s10n2), CurrencyUnit.of("RUB")).unsafeRunSync shouldBe
      Money.of(CurrencyUnit.of("RUB"), 556)
  }
}
