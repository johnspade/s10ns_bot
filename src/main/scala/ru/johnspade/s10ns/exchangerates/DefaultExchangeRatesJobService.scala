package ru.johnspade.s10ns.exchangerates

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import scala.concurrent.duration._

import cats.Apply
import cats.Monad
import cats.effect.Clock
import cats.effect.Concurrent
import cats.effect.Temporal
import cats.implicits._
import cats.~>

import tofu.logging.Logging
import tofu.logging.Logs

import ru.johnspade.s10ns.repeat

class DefaultExchangeRatesJobService[F[_]: Concurrent: Clock: Temporal: Logging, D[_]: Apply](
    private val exchangeRatesService: ExchangeRatesService[F],
    private val exchangeRatesRefreshTimestampRepo: ExchangeRatesRefreshTimestampRepository[D]
)(private implicit val transact: D ~> F)
    extends ExchangeRatesJobService[F] {
  override def startExchangeRatesJob(): F[Unit] =
    for {
      realTime <- Clock[F].realTime
      now = realTime.toMillis
      xRatesRefreshTimestamp <- transact(exchangeRatesRefreshTimestampRepo.get())
      duration = xRatesRefreshTimestamp.map(java.time.Duration.between(_, Instant.ofEpochMilli(now)).toHours)
      initRates = duration match {
        case Some(x) if x > 24 => exchangeRatesService.saveRates()
        case None              => exchangeRatesService.saveRates()
        case _                 => Monad[F].unit
      }
      midnight = OffsetDateTime
        .ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault)
        .truncatedTo(ChronoUnit.DAYS)
        .plusDays(1)
        .toInstant
        .toEpochMilli
      _ <- Concurrent[F].start(
        initRates >> Temporal[F].sleep((midnight - now).millis) >> repeat(exchangeRatesService.saveRates(), 24.hours)
      )
    } yield ()
}

object DefaultExchangeRatesJobService {
  def apply[F[_]: Concurrent: Clock: Temporal, D[_]: Apply](
      exchangeRatesService: ExchangeRatesService[F],
      exchangeRatesRefreshTimestampRepo: ExchangeRatesRefreshTimestampRepository[D]
  )(implicit transact: D ~> F, logs: Logs[F, F]): F[DefaultExchangeRatesJobService[F, D]] =
    Logs[F, F].forService[DefaultExchangeRatesJobService[F, D]].map { implicit l =>
      new DefaultExchangeRatesJobService[F, D](exchangeRatesService, exchangeRatesRefreshTimestampRepo)
    }
}
