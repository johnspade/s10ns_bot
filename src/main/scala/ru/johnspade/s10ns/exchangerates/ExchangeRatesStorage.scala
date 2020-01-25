package ru.johnspade.s10ns.exchangerates

trait ExchangeRatesStorage[F[_]] {
  def getRates: F[Map[String, BigDecimal]]
}
