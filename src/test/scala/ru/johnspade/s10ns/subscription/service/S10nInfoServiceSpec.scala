package ru.johnspade.s10ns.subscription.service

import java.time.temporal.ChronoUnit
import java.time.{LocalDate, ZoneOffset}

import cats.effect.{Clock, IO}
import org.joda.money.{CurrencyUnit, Money}
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ru.johnspade.s10ns.subscription.tags.{BillingPeriodDuration, FirstPaymentDate}
import ru.johnspade.s10ns.subscription.{BillingPeriod, BillingPeriodUnit, RemainingTime}

import cats.effect.unsafe.implicits.global

class S10nInfoServiceSpec extends AnyFlatSpec with Matchers with OptionValues {
  private val s10nInfoService = new S10nInfoService[IO]

  private val amount = Money.of(CurrencyUnit.USD, 13.37)
  private val periodDuration = 2
  private val billingPeriod = BillingPeriod(BillingPeriodDuration(periodDuration), BillingPeriodUnit.Day)
  private val firstPaymentDate = FirstPaymentDate(LocalDate.now(ZoneOffset.UTC).minusDays(1))

  "getNextPaymentDate" should "calculate a next payment date" in {
    val result = s10nInfoService.getNextPaymentDate(firstPaymentDate, Some(billingPeriod)).unsafeRunSync()
    result shouldBe firstPaymentDate.plusDays(periodDuration)
  }

  "getNextPaymentTimestamp" should "calculate a next payment timestamp" in {
    val result = s10nInfoService.getNextPaymentTimestamp(firstPaymentDate, Some(billingPeriod)).unsafeRunSync()
    result shouldBe firstPaymentDate.plusDays(periodDuration).atStartOfDay(ZoneOffset.UTC).toInstant
  }

  "getPaidInTotal" should "calculate paid in total" in {
    val firstPaymentDate = FirstPaymentDate(LocalDate.now(ZoneOffset.UTC).minusDays(periodDuration + 1))
    s10nInfoService.getPaidInTotal(amount, firstPaymentDate, billingPeriod)
      .unsafeRunSync() shouldBe Money.of(CurrencyUnit.USD, 26.74)
  }

  it should "not calculate paid in total for future subscriptions" in {
    s10nInfoService.getPaidInTotal(amount, FirstPaymentDate(LocalDate.now(ZoneOffset.UTC).plusMonths(1)), billingPeriod)
      .unsafeRunSync() shouldBe Money.of(CurrencyUnit.USD, 0)
  }

  "getRemainingTime" should "calculate time left" in {
    val result = s10nInfoService.getRemainingTime(firstPaymentDate.plusDays(periodDuration)).unsafeRunSync()
    result.value shouldBe RemainingTime(ChronoUnit.DAYS, 1)
  }

  it should "calculate time left in years" in {
    val firstPaymentDate = FirstPaymentDate(LocalDate.now(ZoneOffset.UTC).plusYears(1))
    val result = s10nInfoService.getRemainingTime(firstPaymentDate).unsafeRunSync()
    result.value shouldBe RemainingTime(ChronoUnit.YEARS, 1)
  }

  it should "calculate time left in months" in {
    val firstPaymentDate = FirstPaymentDate(LocalDate.now(ZoneOffset.UTC).plusDays(32))
    val result = s10nInfoService.getRemainingTime(firstPaymentDate).unsafeRunSync()
    result.value shouldBe RemainingTime(ChronoUnit.MONTHS, 1)
  }
}
