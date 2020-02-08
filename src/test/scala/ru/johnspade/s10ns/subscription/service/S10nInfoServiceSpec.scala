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
  private val periodDuration = 2
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

  "getBillingPeriod" should "output a billing period with plural count of chrono units" in {
    val billingPeriodDays = BillingPeriod(BillingPeriodDuration(18), BillingPeriodUnit.Day)
    s10nInfoService.getBillingPeriod(billingPeriodDays) shouldBe "_Billing period:_ every 18 days"

    val billingPeriodWeeks = BillingPeriod(BillingPeriodDuration(3), BillingPeriodUnit.Week)
    s10nInfoService.getBillingPeriod(billingPeriodWeeks) shouldBe "_Billing period:_ every 3 weeks"

    val billingPeriodMonths = BillingPeriod(BillingPeriodDuration(2), BillingPeriodUnit.Month)
    s10nInfoService.getBillingPeriod(billingPeriodMonths) shouldBe "_Billing period:_ every 2 months"

    val billingPeriodYears = BillingPeriod(BillingPeriodDuration(5), BillingPeriodUnit.Year)
    s10nInfoService.getBillingPeriod(billingPeriodYears) shouldBe "_Billing period:_ every 5 years"
  }

  it should "output a billing period with a single chrono unit" in {
    val billingPeriodDay = BillingPeriod(BillingPeriodDuration(1), BillingPeriodUnit.Day)
    s10nInfoService.getBillingPeriod(billingPeriodDay) shouldBe "_Billing period:_ every 1 day"

    val billingPeriodWeek = BillingPeriod(BillingPeriodDuration(1), BillingPeriodUnit.Week)
    s10nInfoService.getBillingPeriod(billingPeriodWeek) shouldBe "_Billing period:_ every 1 week"

    val billingPeriodMonth = BillingPeriod(BillingPeriodDuration(1), BillingPeriodUnit.Month)
    s10nInfoService.getBillingPeriod(billingPeriodMonth) shouldBe "_Billing period:_ every 1 month"

    val billingPeriodYear = BillingPeriod(BillingPeriodDuration(1), BillingPeriodUnit.Year)
    s10nInfoService.getBillingPeriod(billingPeriodYear) shouldBe "_Billing period:_ every 1 year"
  }

  "getNextPaymentDate" should "calculate a next payment date" in {
    s10nInfoService.getNextPaymentDate(firstPaymentDate, billingPeriod).unsafeRunSync shouldBe
      s"_Next payment:_ ${DateTimeFormatter.ISO_DATE.format(firstPaymentDate.plusDays(periodDuration))}"
  }

  it should "calculate a next payment date in future" in {
    val firstPaymentDate = FirstPaymentDate(LocalDate.now.plusMonths(1))
    s10nInfoService.getNextPaymentDate(firstPaymentDate, BillingPeriod(BillingPeriodDuration(1), BillingPeriodUnit.Month))
      .unsafeRunSync shouldBe s"_Next payment:_ ${DateTimeFormatter.ISO_DATE.format(firstPaymentDate)}"
  }

  "getPaidInTotal" should "calculate paid in total" in {
    val firstPaymentDate = FirstPaymentDate(LocalDate.now.minusDays(periodDuration + 1))
    s10nInfoService.getPaidInTotal(amount, firstPaymentDate, billingPeriod)
      .unsafeRunSync shouldBe "_Paid in total:_ 26.74 $"
  }

  it should "not calculate paid in total for future subscriptions" in {
    s10nInfoService.getPaidInTotal(amount, FirstPaymentDate(LocalDate.now.plusMonths(1)), billingPeriod)
      .unsafeRunSync shouldBe "_Paid in total:_ 0.00 $"
  }

  "getFirstPaymentDate" should "print a first payment date" in {
    s10nInfoService.getFirstPaymentDate(firstPaymentDate) shouldBe
      s"_First payment:_ ${DateTimeFormatter.ISO_DATE.format(firstPaymentDate)}"
  }
}
