package ru.johnspade.s10ns.exchangerates

import java.time.{Instant, LocalDate, LocalDateTime, LocalTime, ZoneId}
import java.util.concurrent.TimeUnit

import cats.effect.{Clock, Concurrent, Sync, Timer}
import cats.implicits._
import cats.{Apply, ~>}
import io.chrisdavenport.log4cats.Logger

import scala.concurrent.duration._

class ExchangeRatesJobService[F[_] : Concurrent : Clock : Timer : Logger, D[_] : Apply](
  private val exchangeRatesService: ExchangeRatesService[F, D],
  private val exchangeRatesRefreshTimestampRepo: ExchangeRatesRefreshTimestampRepository[D]
)(private implicit val transact: D ~> F) {
  def startExchangeRatesJob(): F[Unit] = {
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
      midnight <- Sync[F].delay {
        LocalDateTime.of(LocalDate.now, LocalTime.MIDNIGHT).plusDays(1).atZone(ZoneId.systemDefault()).toInstant.toEpochMilli
      }
      _ <- Concurrent[F].start(
        initRates >> Timer[F].sleep((midnight - now).millis) >> repeatDaily(exchangeRatesService.saveRates())
      )
    } yield ()
  }
}
