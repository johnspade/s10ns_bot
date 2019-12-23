package ru.johnspade.s10ns.money

import java.math.RoundingMode
import java.time.temporal.ChronoUnit

import cats.effect.Sync
import cats.implicits._
import org.joda.money.{BigMoney, CurrencyUnit, Money}
import ru.johnspade.s10ns.exchangerates.ExchangeRatesService
import ru.johnspade.s10ns.subscription.{BillingPeriod, Subscription}

class MoneyService[F[_] : Sync](private val exchangeRatesService: ExchangeRatesService[F]) {
  def sum(subscriptions: Seq[Subscription], defaultCurrency: CurrencyUnit): F[Option[Money]] = {
    def convertToEuro(amount: Money, rate: BigDecimal): BigMoney =
      if (amount.getCurrencyUnit == CurrencyUnit.EUR)
        amount.toBigMoney
      else {
        amount.toBigMoney.withScale(10).dividedBy(rate.bigDecimal, RoundingMode.HALF_UP)
          .convertedTo(CurrencyUnit.EUR, BigDecimal(1).bigDecimal)
      }

    def convertToDefaultCurrency(amount: BigMoney, rate: BigDecimal) =
      amount.convertedTo(defaultCurrency, rate.bigDecimal)

    def getSubscriptionAmountEuro(s: Subscription, rates: Map[String, BigDecimal]) =
      s.billingPeriod.flatMap { period =>
        rates.get(s.amount.getCurrencyUnit.getCode).map { rate =>
          (period, convertToEuro(s.amount, rate))
        }
      }

    def calcMonthAmount(period: BillingPeriod, amount: BigMoney) =
      amount
        .toBigMoney
        .withScale(10)
        .dividedBy(period.unit.getDuration.getSeconds * period.duration, RoundingMode.HALF_UP)
        .multipliedBy(ChronoUnit.MONTHS.getDuration.getSeconds)

    exchangeRatesService.getRates.map { rates =>
      val sum = subscriptions
        .flatMap(s => getSubscriptionAmountEuro(s, rates))
        .map {
          case (period, money) => calcMonthAmount(period, money)
        }
        .foldLeft(Money.zero(CurrencyUnit.EUR).toBigMoney.withScale(10))(_ plus _)
      rates.get(defaultCurrency.getCode)
        .map(rate => convertToDefaultCurrency(sum, rate.bigDecimal).toMoney(RoundingMode.HALF_UP))
    }
  }

  def convert(money: Money, currency: CurrencyUnit): F[Option[Money]] =
    exchangeRatesService.getRates.map { rates =>
      (rates.get(money.getCurrencyUnit.getCode), rates.get(currency.getCode)) match {
        case (Some(moneyRate), Some(currencyRate)) =>
          money
            .toBigMoney
            .withScale(10)
            .dividedBy(moneyRate.bigDecimal, RoundingMode.HALF_UP)
            .convertedTo(currency, currencyRate.bigDecimal)
            .toMoney(RoundingMode.HALF_UP)
            .some
        case _ => Option.empty[Money]
      }
    }
}
