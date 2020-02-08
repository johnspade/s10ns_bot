package ru.johnspade.s10ns.subscription.service

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}
import java.util.concurrent.TimeUnit

import cats.Monad
import cats.effect.{Clock, Sync}
import cats.implicits._
import com.ibm.icu.text.MeasureFormat
import com.ibm.icu.text.MeasureFormat.FormatWidth
import com.ibm.icu.util.{Measure, ULocale}
import org.joda.money.{CurrencyUnit, Money}
import ru.johnspade.s10ns.bot.Formatters.MoneyFormatter
import ru.johnspade.s10ns.bot.MoneyService
import ru.johnspade.s10ns.subscription.BillingPeriod
import ru.johnspade.s10ns.subscription.tags.{FirstPaymentDate, SubscriptionName}

class S10nInfoService[F[_] : Sync : Clock](
  private val moneyService: MoneyService[F]
) {
  def getName(name: SubscriptionName): String = s"*$name*"

  def getAmount(amount: Money): String = MoneyFormatter.print(amount)

  def getAmountInDefaultCurrency(amount: Money, defaultCurrency: CurrencyUnit): F[Option[String]] = {
    val converted =
      if (amount.getCurrencyUnit == defaultCurrency) Monad[F].pure(Option.empty[Money])
      else moneyService.convert(amount, defaultCurrency)
    converted.map(_.map(a => s"≈${MoneyFormatter.print(a)}"))
  }

  def getBillingPeriod(period: BillingPeriod): String = {
    val measure = measureFormat.format(new Measure(period.duration, period.unit.measureUnit))
    s"_Billing period:_ every $measure"
  }

  def getNextPaymentDate(start: FirstPaymentDate, billingPeriod: BillingPeriod): F[String] = {
    import billingPeriod.duration
    import billingPeriod.unit.chronoUnit

    nowClock.map { now =>
      val today = now.atZone(ZoneOffset.UTC).toLocalDate
      val nextPaymentDate = if (!today.isAfter(start)) start
      else {
        val unitsPassed = chronoUnit.between(start, today)
        val periodsPassed = unitsPassed / duration
        val cycleEnd = if (unitsPassed < duration) start.plus(duration, chronoUnit)
        else start.plus(duration * (periodsPassed + 1), chronoUnit)
        if (cycleEnd isBefore today) cycleEnd.plus(duration, chronoUnit) else cycleEnd
      }
      s"_Next payment:_ ${DateTimeFormatter.ISO_DATE.format(nextPaymentDate)}"
    }
  }

  def getPaidInTotal(amount: Money, start: FirstPaymentDate, billingPeriod: BillingPeriod): F[String] = {
    import billingPeriod.duration
    import billingPeriod.unit.chronoUnit

    nowClock.map { now =>
      val today = now.atZone(ZoneOffset.UTC).toLocalDate
      val paymentsCount = if (!today.isAfter(start)) 0
      else {
        val unitsPassed = chronoUnit.between(start, today)
        val periodsPassed = unitsPassed / duration
        val cycleEnd = if (unitsPassed < duration) start.plus(duration, chronoUnit)
        else start.plus(duration * (periodsPassed + 1), chronoUnit)
        if (cycleEnd isAfter today) periodsPassed + 1 else periodsPassed
      }
      s"_Paid in total:_ ${MoneyFormatter.print(amount.multipliedBy(paymentsCount))}"
    }
  }

  def getFirstPaymentDate(start: FirstPaymentDate): String = s"_First payment:_ ${DateTimeFormatter.ISO_DATE.format(start)}"

  private def nowClock = Clock[F].realTime(TimeUnit.MILLISECONDS).map(Instant.ofEpochMilli)

  private val measureFormat = MeasureFormat.getInstance(ULocale.US, FormatWidth.WIDE)
}
