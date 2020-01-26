package ru.johnspade.s10ns.bot

import java.math.RoundingMode
import java.time.temporal.ChronoUnit

import cats.effect.Sync
import cats.implicits._
import org.joda.money.format.{MoneyFormatter, MoneyFormatterBuilder}
import org.joda.money.{BigMoney, CurrencyUnit, Money}
import ru.johnspade.s10ns.exchangerates.ExchangeRatesStorage
import ru.johnspade.s10ns.subscription.{BillingPeriod, Subscription}

import scala.math.max

class MoneyService[F[_] : Sync](private val exchangeRatesStorage: ExchangeRatesStorage[F]) {
  def sum(subscriptions: List[Subscription], defaultCurrency: CurrencyUnit): F[Money] = {
    def getAmountInDefCurrencyAndPeriod(s10n: Subscription, rates: Map[String, BigDecimal]) =
      s10n.billingPeriod.flatTraverse { period =>
        convert(s10n.amount, defaultCurrency)
          .map(_.map((period, _)))
      }

    def calcMonthAmount(period: BillingPeriod, amount: Money) =
      amount
        .multipliedBy(ChronoUnit.MONTHS.getDuration.getSeconds)
        .dividedBy(period.unit.getDuration.getSeconds * period.duration, RoundingMode.HALF_EVEN)

    exchangeRatesStorage.getRates.flatMap { rates =>
      subscriptions
        .traverse(getAmountInDefCurrencyAndPeriod(_, rates))
        .map {
          _.flatten
            .map {
              case (period, amount) =>
                calcMonthAmount(period, amount)
            }
            .foldLeft(Money.zero(defaultCurrency))(_ plus _)
        }
    }
  }

  def convert(money: Money, currency: CurrencyUnit): F[Option[Money]] =
    exchangeRatesStorage.getRates.map { rates =>
      (rates.get(money.getCurrencyUnit.getCode), rates.get(currency.getCode)) match {
        case (Some(moneyRate), Some(currencyRate)) =>
          val scale = max(moneyRate.scale, currencyRate.scale)
          val rate = currencyRate.bigDecimal.divide(moneyRate.bigDecimal, scale, RoundingMode.HALF_EVEN)
          money.convertedTo(currency, rate, RoundingMode.HALF_EVEN).some
        case _ => Option.empty[Money]
      }
    }

  val MoneyFormatter: MoneyFormatter =
    new MoneyFormatterBuilder()
      .appendAmount()
      .appendLiteral(" ")
      .appendCurrencySymbolLocalized()
      .toFormatter
}
