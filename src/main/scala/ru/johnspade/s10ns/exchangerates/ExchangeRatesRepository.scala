package ru.johnspade.s10ns.exchangerates

trait ExchangeRatesRepository[D[_]] {
  def save(rates: Map[String, BigDecimal]): D[Unit]

  def get(): D[Map[String, BigDecimal]]

  def clear(): D[Unit]
}
