package ru.johnspade.s10ns.bot

import java.time.temporal.ChronoUnit

import cats.effect.IO
import cats.implicits._
import com.softwaremill.quicklens._
import org.joda.money.{CurrencyUnit, Money}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ru.johnspade.s10ns.exchangerates.{ExchangeRatesStorage, InMemoryExchangeRatesStorage}
import ru.johnspade.s10ns.subscription.tags.{BillingPeriodDuration, SubscriptionId, SubscriptionName}
import ru.johnspade.s10ns.subscription.{BillingPeriod, BillingPeriodUnit, Subscription}
import ru.johnspade.s10ns.user.tags.UserId
import cats.effect.unsafe.implicits.global

class MoneyServiceSpec extends AnyFlatSpec with Matchers {
  private val exchangeRatesStorage: ExchangeRatesStorage[IO] = new InMemoryExchangeRatesStorage
  private val moneyService = new MoneyService[IO](exchangeRatesStorage)

  private val rub = CurrencyUnit.of("RUB")
  private val oneMonth = BillingPeriod(BillingPeriodDuration(1), BillingPeriodUnit.Month)

  behavior of "convert"

  it should "convert between currencies correctly" in {
    val amountRub = Money.of(rub, 1598)
    moneyService.convert(amountRub, CurrencyUnit.USD).unsafeRunSync() shouldBe
      Money.of(CurrencyUnit.USD, 25.68).some

    val amountUsd = Money.of(CurrencyUnit.USD, 15)
    moneyService.convert(amountUsd, CurrencyUnit.EUR).unsafeRunSync() shouldBe
      Money.of(CurrencyUnit.EUR, 13.43).some
  }

  it should "not change amount in the same currency" in {
    val amountUsd = Money.of(CurrencyUnit.USD, 25.68)
    moneyService.convert(amountUsd, CurrencyUnit.USD).unsafeRunSync() shouldBe amountUsd.some
  }

  behavior of "sum"

  it should "sum subscriptions with a default currency" in {
    val s10ns = List(
      createS10n(Money.of(rub, 256), oneMonth.some),
      createS10n(Money.of(rub, 300), oneMonth.some)
    )
    moneyService.sum(s10ns, rub).unsafeRunSync() shouldBe Money.of(rub, 556)
  }

  it should "sum subscriptions with different currencies" in {
    val s10ns = List(
      createS10n(Money.of(CurrencyUnit.USD, 25.68), oneMonth.some),
      createS10n(Money.of(rub, 576.32), oneMonth.some)
    )
    moneyService.sum(s10ns, CurrencyUnit.EUR).unsafeRunSync() shouldBe Money.of(CurrencyUnit.EUR, 31.29)
  }

  it should "sum subscriptions with different billing periods" in {
    val s10ns = List(
      createS10n(Money.of(rub, 256), BillingPeriod(BillingPeriodDuration(2), BillingPeriodUnit.Week).some),
      createS10n(Money.of(rub, 10), BillingPeriod(BillingPeriodDuration(1), BillingPeriodUnit.Day).some)
    )
    moneyService.sum(s10ns, rub).unsafeRunSync() shouldBe Money.of(rub, 860.93)
  }

  it should "ignore one time subscriptions" in {
    val s10ns = List(
      createS10n(
        Money.of(CurrencyUnit.EUR, 10),
        BillingPeriod(BillingPeriodDuration(1), BillingPeriodUnit.Month).some
      ),
      createS10n(Money.of(CurrencyUnit.EUR, 20))
    )

    moneyService.sum(s10ns, CurrencyUnit.EUR).unsafeRunSync() shouldBe Money.of(CurrencyUnit.EUR, 10)
  }

  behavior of "calcAmount"

  it should "return a passed amount if periods are equal" in {
    moneyService.calcAmount(
      BillingPeriod(BillingPeriodDuration(1), BillingPeriodUnit.Month),
      Money.of(rub, 256),
      ChronoUnit.MONTHS
    ) shouldBe Money.of(rub, 256)
  }

  it should "calculate an amount correctly if a period is larger than a billing period" in {
    moneyService.calcAmount(
      BillingPeriod(BillingPeriodDuration(1), BillingPeriodUnit.Day),
      Money.of(rub, 256),
      ChronoUnit.MONTHS
    ) shouldBe Money.of(rub, 7791.84)
  }

  it should "calculate an amount correctly if a period is less than a billing period" in {
    moneyService.calcAmount(
      BillingPeriod(BillingPeriodDuration(2), BillingPeriodUnit.Year),
      Money.of(rub, 256),
      ChronoUnit.WEEKS
    ) shouldBe Money.of(rub, 2.45)
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

  private def createS10n(amount: Money, period: Option[BillingPeriod] = None) =
    s10n.modify(_.amount).setTo(amount).modify(_.billingPeriod).setTo(period)
}
