package ru.johnspade.s10ns.bot

import java.math.RoundingMode
import java.time.temporal.ChronoUnit

import cats.Monad
import cats.implicits._
import org.joda.money.{CurrencyUnit, Money}
import ru.johnspade.s10ns.exchangerates.ExchangeRatesStorage
import ru.johnspade.s10ns.subscription.{BillingPeriod, Subscription}

import scala.math.max

class MoneyService[F[_]: Monad](private val exchangeRatesStorage: ExchangeRatesStorage[F]) {
  def sum(subscriptions: List[Subscription], defaultCurrency: CurrencyUnit, unit: ChronoUnit = ChronoUnit.MONTHS): F[Money] =
    subscriptions
      .traverse { s10n =>
        s10n.billingPeriod.flatTraverse { period =>
          convert(calcAmount(period, s10n.amount, unit), defaultCurrency)
        }
      }
      .map {
        _.flatten
          .foldLeft(Money.zero(defaultCurrency))(_ plus _)
      }

  def calcAmount(period: BillingPeriod, amount: Money, unit: ChronoUnit = ChronoUnit.MONTHS): Money =
    amount
      .multipliedBy(unit.getDuration.getSeconds)
      .dividedBy(period.unit.chronoUnit.getDuration.getSeconds * period.duration, RoundingMode.HALF_EVEN)

  def convert(amount: Money, currency: CurrencyUnit): F[Option[Money]] = {
    def calcRateAndConvert(moneyRate: BigDecimal, targetRate: BigDecimal) = {
      val scale = max(moneyRate.scale, targetRate.scale)
      val rate = targetRate.bigDecimal.divide(moneyRate.bigDecimal, scale, RoundingMode.HALF_EVEN)
      amount.convertedTo(currency, rate, RoundingMode.HALF_EVEN).some
    }

    exchangeRatesStorage.getRates
      .map { rates =>
        (rates.get(amount.getCurrencyUnit.getCode), rates.get(currency.getCode)) match {
          case (Some(moneyRate), Some(currencyRate)) => calcRateAndConvert(moneyRate, currencyRate)
          case _ => Option.empty[Money]
        }
      }
  }
}
