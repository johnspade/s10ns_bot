package ru.johnspade.s10ns.subscription.service

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, ZoneOffset}
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
  private implicit val localDateOrdering: Ordering[LocalDate] = _ compareTo _

  def getName(name: SubscriptionName): String = s"*$name*"

  def getAmount(amount: Money): String = MoneyFormatter.print(amount)

  def printAmount(amount: Money, defaultCurrency: CurrencyUnit): F[String] =
    getAmountInDefaultCurrency(amount, defaultCurrency).map(_.getOrElse(MoneyFormatter.print(amount)))

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

  def getNextPaymentDate(start: FirstPaymentDate, billingPeriod: BillingPeriod): F[String] =
    calculatePeriodsPassed(start, billingPeriod).map { periodsPassed =>
      val nextPaymentDate = start.plus(periodsPassed * billingPeriod.duration, billingPeriod.unit.chronoUnit)
      s"_Next payment:_ ${DateTimeFormatter.ISO_DATE.format(nextPaymentDate)}"
    }

  def getPaidInTotal(amount: Money, start: FirstPaymentDate, billingPeriod: BillingPeriod): F[String] =
    calculatePeriodsPassed(start, billingPeriod).map { paymentsCount =>
      s"_Paid in total:_ ${MoneyFormatter.print(amount.multipliedBy(paymentsCount))}"
    }

  def getFirstPaymentDate(start: FirstPaymentDate): String = s"_First payment:_ ${DateTimeFormatter.ISO_DATE.format(start)}"

  private def calculatePeriodsPassed(start: FirstPaymentDate, billingPeriod: BillingPeriod) = {
    import billingPeriod.duration
    import billingPeriod.unit.chronoUnit

    Clock[F].realTime(TimeUnit.MILLISECONDS).map { millis =>
      val today = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate
      val unitsPassed = chronoUnit.between(start, Set(today.plus(duration, chronoUnit).minusDays(1), start).max)
      unitsPassed / duration
    }
  }

  private val measureFormat = MeasureFormat.getInstance(ULocale.US, FormatWidth.WIDE)
}
