package ru.johnspade.s10ns.exchangerates

import java.time.{Instant, OffsetDateTime, ZoneId}
import java.util.concurrent.TimeUnit

import cats.effect.{Clock, Concurrent, Sync, Timer}
import cats.implicits._
import cats.{Apply, ~>}
import io.chrisdavenport.log4cats.Logger

import scala.concurrent.duration._

class DefaultExchangeRatesJobService[F[_] : Concurrent : Clock : Timer : Logger, D[_] : Apply](
  private val exchangeRatesService: ExchangeRatesService[F],
  private val exchangeRatesRefreshTimestampRepo: ExchangeRatesRefreshTimestampRepository[D]
)(private implicit val transact: D ~> F) extends ExchangeRatesJobService[F] {
  override def startExchangeRatesJob(): F[Unit] = {
    def repeatDaily(io: F[Unit]): F[Unit] = io >> Timer[F].sleep(24.hours) >> repeatDaily(io)

    for {
      now <- Clock[F].realTime(TimeUnit.MILLISECONDS)
      xRatesRefreshTimestamp <- transact(exchangeRatesRefreshTimestampRepo.get())
      duration = xRatesRefreshTimestamp.map(java.time.Duration.between(_, Instant.ofEpochMilli(now)).toHours)
      initRates = duration match {
        case Some(x) if x > 24 => exchangeRatesService.saveRates()
        case None => exchangeRatesService.saveRates()
        case _ => Sync[F].unit
      }
      midnight = OffsetDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault)
        .withHour(0)
        .plusDays(1)
        .toInstant
        .toEpochMilli
      _ <- Concurrent[F].start(
        initRates >> Timer[F].sleep((midnight - now).millis) >> repeatDaily(exchangeRatesService.saveRates())
      )
    } yield ()
  }
}
