package ru.johnspade.s10ns.exchangerates

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._

trait ExchangeRatesCache[F[_]] {
  def get: F[Map[String, BigDecimal]]

  def set(rates: Map[String, BigDecimal]): F[Unit]
}

object ExchangeRatesCache {
  def create[F[_] : Sync](rates: Map[String, BigDecimal]): F[ExchangeRatesCache[F]] =
    Ref.of(rates).map { r =>
      new ExchangeRatesCache[F] {
        override def get: F[Map[String, BigDecimal]] = r.get

        override def set(rates: Map[String, BigDecimal]): F[Unit] = r.set(rates)
      }
    }
}
