package ru.johnspade.s10ns.exchangerates

import java.time.Instant

import cats.Id

class InMemoryExchangeRatesRefreshTimestampRepository extends ExchangeRatesRefreshTimestampRepository[Id] {
  private var timestamp: Option[Instant] = None

  override def save(newTimestamp: Instant): Unit = {
    timestamp = Some(newTimestamp)
  }

  override def get(): Option[Instant] = timestamp
}
