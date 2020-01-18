package ru.johnspade.s10ns.exchangerates

import java.time.Instant

trait ExchangeRatesRefreshTimestampRepository[D[_]] {
  def save(timestamp: Instant): D[Unit]

  def get(): D[Option[Instant]]
}
