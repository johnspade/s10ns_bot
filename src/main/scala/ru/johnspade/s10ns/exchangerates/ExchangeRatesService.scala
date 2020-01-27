package ru.johnspade.s10ns.exchangerates

trait ExchangeRatesService[F[_]] extends ExchangeRatesStorage[F] {
  def saveRates(): F[Unit]
}
