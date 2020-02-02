package ru.johnspade.s10ns.subscription.service

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import cats.effect.{Clock, IO}
import org.joda.money.{CurrencyUnit, Money}
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ru.johnspade.s10ns.bot.MoneyService
import ru.johnspade.s10ns.exchangerates.InMemoryExchangeRatesStorage
import ru.johnspade.s10ns.subscription.tags.{BillingPeriodDuration, FirstPaymentDate, SubscriptionName}
import ru.johnspade.s10ns.subscription.{BillingPeriod, BillingPeriodUnit}

import scala.concurrent.ExecutionContext

class S10nInfoServiceSpec extends AnyFlatSpec with Matchers with OptionValues {
  private implicit val clock: Clock[IO] = IO.timer(ExecutionContext.global).clock
  private val exchangeRatesStorage = new InMemoryExchangeRatesStorage
  private val moneyService = new MoneyService[IO](exchangeRatesStorage)
  private val s10nInfoService = new S10nInfoService[IO](moneyService)

  private val amount = Money.of(CurrencyUnit.USD, 13.37)
  private val periodDuration = 17
  private val billingPeriod = BillingPeriod(BillingPeriodDuration(periodDuration), BillingPeriodUnit.Day)
  private val firstPaymentDate = FirstPaymentDate(LocalDate.now.minusDays(1))

  "getName" should "get subscription's name" in {
    s10nInfoService.getName(SubscriptionName("Netflix")) shouldBe "*Netflix*"
  }

  "getAmount" should "print subscription's amount" in {
    s10nInfoService.getAmount(amount) shouldBe "13.37 $"
  }

  "getAmountInDefaultCurrency" should "convert the amount to default currency" in {
    s10nInfoService.getAmountInDefaultCurrency(amount, CurrencyUnit.EUR).unsafeRunSync.value shouldBe "≈11.97 EUR"
  }

  "getBillingPeriod" should "output a billing period" in {
    s10nInfoService.getBillingPeriod(billingPeriod) shouldBe "_Billing period:_ every 17 days"
  }

  "getNextPaymentDate" should "calculate a next payment date" in {
    s10nInfoService.getNextPaymentDate(firstPaymentDate, billingPeriod).unsafeRunSync shouldBe
      s"_Next payment:_ ${DateTimeFormatter.ISO_DATE.format(firstPaymentDate.plusDays(periodDuration + 1))}"
  }

  "getPaidInTotal" should "calculate paid in total" in {
    s10nInfoService.getPaidInTotal(amount, FirstPaymentDate(LocalDate.now.minusDays(periodDuration * 2)), billingPeriod)
      .unsafeRunSync shouldBe "_Paid in total:_ 26.74 $"
  }

  "getFirstPaymentDate" should "print a first payment date" in {
    s10nInfoService.getFirstPaymentDate(firstPaymentDate) shouldBe
      s"_First payment:_ ${DateTimeFormatter.ISO_DATE.format(firstPaymentDate)}"
  }
}
