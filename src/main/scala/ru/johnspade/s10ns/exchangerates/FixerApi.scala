package ru.johnspade.s10ns.exchangerates

trait FixerApi[F[_]] {
  def getLatestRates: F[ExchangeRates]
}
