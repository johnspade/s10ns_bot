package ru.johnspade.s10ns.exchangerates

import cats.implicits._
import doobie._
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.util.log.LogHandler
import doobie.util.query.Query0
import ru.johnspade.s10ns.exchangerates.DoobieExchangeRatesRepository.ExchangeRatesSql

class DoobieExchangeRatesRepository extends ExchangeRatesRepository {
  override def save(rates: Map[String, BigDecimal]): ConnectionIO[Unit] =
    Update[(String, BigDecimal)](ExchangeRatesSql.Save).updateMany(rates.toList).void

  override def get(): ConnectionIO[Map[String, BigDecimal]] = ExchangeRatesSql.get().to[List].map(_.toMap)

  override def clear(): ConnectionIO[Unit] = ExchangeRatesSql.clear().run.void
}

object DoobieExchangeRatesRepository {
  private object ExchangeRatesSql {
    private implicit val han: LogHandler = LogHandler.jdkLogHandler

    def get(): Query0[(String, BigDecimal)] = sql"""select currency, rate from exchange_rates""".query[(String, BigDecimal)]

    val Save = "insert into exchange_rates (currency, rate) values (?, ?)"

    def clear(): Update0 = sql"""delete from exchange_rates""".update
  }
}
