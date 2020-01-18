package ru.johnspade.s10ns.subscription.service

import java.time.format.DateTimeFormatter
import java.time.{Duration, Instant, ZoneOffset}
import java.util.concurrent.TimeUnit

import cats.Monad
import cats.effect.{Clock, Sync}
import cats.implicits._
import org.joda.money.{CurrencyUnit, Money}
import ru.johnspade.s10ns.bot.MoneyService
import ru.johnspade.s10ns.subscription.BillingPeriod
import ru.johnspade.s10ns.subscription.tags.{FirstPaymentDate, SubscriptionName}

class S10nInfoService[F[_] : Sync : Clock, D[_] : Monad](
  private val moneyService: MoneyService[F, D]
) {
  def getName(name: SubscriptionName): String = s"*$name*"

  def getAmount(amount: Money): String = moneyService.MoneyFormatter.print(amount)

  def getAmountInDefaultCurrency(amount: Money, defaultCurrency: CurrencyUnit): F[Option[String]] = {
    val converted =
      if (amount.getCurrencyUnit == defaultCurrency) Monad[F].pure(Option.empty[Money])
      else moneyService.convert(amount, defaultCurrency)
    converted.map(_.map(a => s"≈${moneyService.MoneyFormatter.print(a)}"))
  }

  def getBillingPeriod(period: BillingPeriod): String = {
    val number = if (period.duration == 1) ""
    else s" ${period.duration}"
    val unitName = period.unit.toString.toLowerCase.reverse.replaceFirst("s", "").reverse
    s"_Billing period:_ every$number $unitName"
  }

  def getNextPaymentDate(start: FirstPaymentDate, billingPeriod: BillingPeriod): F[String] =
    nowClock.map { now =>
      val nextPeriod = now.plusSeconds(billingPeriod.seconds - (secondsPassed(start, now) % billingPeriod.seconds))
      val nextPaymentDate = nextPeriod.atZone(ZoneOffset.UTC).toLocalDate.plusDays(1)
      s"_Next payment:_ ${DateTimeFormatter.ISO_DATE.format(nextPaymentDate)}"
    }

  def getPaidInTotal(amount: Money, start: FirstPaymentDate, billingPeriod: BillingPeriod): F[String] =
    nowClock.map { now =>
      val periodsPassed = secondsPassed(start, now) / billingPeriod.seconds + 1
      s"_Paid in total:_ ${moneyService.MoneyFormatter.print(amount.multipliedBy(periodsPassed))}"
    }

  def getFirstPaymentDate(start: FirstPaymentDate): String = s"_First payment:_ ${DateTimeFormatter.ISO_DATE.format(start)}"

  private def nowClock = Clock[F].realTime(TimeUnit.MILLISECONDS).map(Instant.ofEpochMilli)

  private def toInstant(start: FirstPaymentDate) = start.atStartOfDay().toInstant(ZoneOffset.UTC)
  private def secondsPassed(start: FirstPaymentDate, now: Instant) = Duration.between(toInstant(start), now).getSeconds
}
