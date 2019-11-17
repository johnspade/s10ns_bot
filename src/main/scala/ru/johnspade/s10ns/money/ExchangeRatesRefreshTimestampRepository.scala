package ru.johnspade.s10ns.money

import java.time.Instant

import doobie.free.connection.ConnectionIO

trait ExchangeRatesRefreshTimestampRepository {
  def save(timestamp: Instant): ConnectionIO[Unit]

  def get(): ConnectionIO[Instant]
}
