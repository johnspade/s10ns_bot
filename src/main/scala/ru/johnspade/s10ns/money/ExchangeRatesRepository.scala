package ru.johnspade.s10ns.money

import doobie.free.connection.ConnectionIO

trait ExchangeRatesRepository {
  def save(rates: Map[String, BigDecimal]): ConnectionIO[Unit]

  def get(): ConnectionIO[Map[String, BigDecimal]]

  def clear(): ConnectionIO[Unit]
}
