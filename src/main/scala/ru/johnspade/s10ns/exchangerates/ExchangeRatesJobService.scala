package ru.johnspade.s10ns.exchangerates

trait ExchangeRatesJobService[F[_]] {
  def startExchangeRatesJob(): F[Unit]
}
