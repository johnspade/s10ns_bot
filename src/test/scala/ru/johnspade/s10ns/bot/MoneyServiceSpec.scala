package ru.johnspade.s10ns.bot

import java.time.temporal.ChronoUnit

import cats.effect.IO
import org.joda.money.{CurrencyUnit, Money}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ru.johnspade.s10ns.exchangerates.ExchangeRatesStorage
import cats.implicits._
import com.softwaremill.quicklens._
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

  private val rub = CurrencyUnit.of("RUB")
  private val oneMonth = BillingPeriod(BillingPeriodDuration(1), BillingPeriodUnit(ChronoUnit.MONTHS))

  behavior of "convert"

  it should "convert between currencies correctly" in {
    val amountRub = Money.of(rub, 1598)
    moneyService.convert(amountRub, CurrencyUnit.USD).unsafeRunSync shouldBe
      Money.of(CurrencyUnit.USD, 25.68).some

    val amountUsd = Money.of(CurrencyUnit.USD, 15)
    moneyService.convert(amountUsd, CurrencyUnit.EUR).unsafeRunSync shouldBe
      Money.of(CurrencyUnit.EUR, 13.43).some
  }

  it should "not change amount in the same currency" in {
    val amountUsd = Money.of(CurrencyUnit.USD, 25.68)
    moneyService.convert(amountUsd, CurrencyUnit.USD).unsafeRunSync shouldBe amountUsd.some
  }

  behavior of "sum"

  it should "sum subscriptions with a default currency" in {
    val s10ns = List(
      createS10n(Money.of(rub, 256), oneMonth),
      createS10n(Money.of(rub, 300), oneMonth)
    )
    moneyService.sum(s10ns, rub).unsafeRunSync shouldBe Money.of(rub, 556)
  }

  it should "sum subscriptions with different currencies" in {
    val s10ns = List(
      createS10n(Money.of(CurrencyUnit.USD, 25.68), oneMonth),
      createS10n(Money.of(rub, 576.32), oneMonth)
    )
    moneyService.sum(s10ns, CurrencyUnit.EUR).unsafeRunSync shouldBe Money.of(CurrencyUnit.EUR, 31.29)
  }

  it should "sum subscriptions with different billing periods" in {
    val s10ns = List(
      createS10n(Money.of(rub, 256), BillingPeriod(BillingPeriodDuration(2), BillingPeriodUnit(ChronoUnit.WEEKS))),
      createS10n(Money.of(rub, 10), BillingPeriod(BillingPeriodDuration(1), BillingPeriodUnit(ChronoUnit.DAYS)))
    )
    moneyService.sum(s10ns, rub).unsafeRunSync shouldBe Money.of(rub, 860.93)
  }

  private val s10n = Subscription(
    SubscriptionId(1L),
    UserId(0L),
    SubscriptionName("s10n"),
    Money.zero(rub),
    None,
    None,
    None
  )

  private def createS10n(amount: Money, period: BillingPeriod) =
    s10n.modify(_.amount).setTo(amount).modify(_.billingPeriod).setTo(period.some)
}
