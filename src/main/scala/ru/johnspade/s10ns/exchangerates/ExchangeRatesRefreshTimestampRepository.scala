package ru.johnspade.s10ns.exchangerates

import java.time.Instant

import doobie.free.connection.ConnectionIO

trait ExchangeRatesRefreshTimestampRepository {
  def save(timestamp: Instant): ConnectionIO[Unit]

  def get(): ConnectionIO[Option[Instant]]
}
