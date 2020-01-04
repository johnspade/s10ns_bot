package ru.johnspade.s10ns.exchangerates

import java.time.Instant

import cats.implicits._
import doobie._
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.util.log.LogHandler
import ru.johnspade.s10ns.exchangerates.DoobieExchangeRatesRefreshTimestampRepository.ExchangeRatesRefreshTimestampSql

class DoobieExchangeRatesRefreshTimestampRepository extends ExchangeRatesRefreshTimestampRepository {
  override def save(timestamp: Instant): ConnectionIO[Unit] = ExchangeRatesRefreshTimestampSql.save(timestamp).run.void

  override def get(): ConnectionIO[Option[Instant]] = ExchangeRatesRefreshTimestampSql.get().option
}

object DoobieExchangeRatesRefreshTimestampRepository {
  private object ExchangeRatesRefreshTimestampSql {
    private implicit val han: LogHandler = LogHandler.jdkLogHandler

    def get(): Query0[Instant] = sql"select refresh_timestamp from exchange_rates_refresh_timestamp".query[Instant]

    def save(timestamp: Instant): Update0 =
      sql"update exchange_rates_refresh_timestamp set refresh_timestamp = $timestamp where id = true".update
  }
}
