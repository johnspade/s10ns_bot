package ru.johnspade.s10ns.exchangerates

import java.time.temporal.ChronoUnit
import java.time.{Instant, OffsetDateTime, ZoneId}

import cats.effect.{Clock, Concurrent, Sync, Timer}
import cats.implicits._
import cats.{Apply, ~>}
import ru.johnspade.s10ns.repeat
import tofu.logging.{Logging, Logs}

import scala.concurrent.duration._

class DefaultExchangeRatesJobService[F[_]: Concurrent: Clock: Timer: Logging, D[_]: Apply](
  private val exchangeRatesService: ExchangeRatesService[F],
  private val exchangeRatesRefreshTimestampRepo: ExchangeRatesRefreshTimestampRepository[D]
)(private implicit val transact: D ~> F) extends ExchangeRatesJobService[F] {
  override def startExchangeRatesJob(): F[Unit] =
    for {
      now <- Clock[F].realTime(MILLISECONDS)
      xRatesRefreshTimestamp <- transact(exchangeRatesRefreshTimestampRepo.get())
      duration = xRatesRefreshTimestamp.map(java.time.Duration.between(_, Instant.ofEpochMilli(now)).toHours)
      initRates = duration match {
        case Some(x) if x > 24 => exchangeRatesService.saveRates()
        case None => exchangeRatesService.saveRates()
        case _ => Sync[F].unit
      }
      midnight = OffsetDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault)
        .truncatedTo(ChronoUnit.DAYS)
        .plusDays(1)
        .toInstant
        .toEpochMilli
      _ <- Concurrent[F].start(
        initRates >> Timer[F].sleep((midnight - now).millis) >> repeat(exchangeRatesService.saveRates(), 24.hours)
      )
    } yield ()
}

object DefaultExchangeRatesJobService {
  def apply[F[_]: Concurrent: Clock: Timer, D[_]: Apply](
    exchangeRatesService: ExchangeRatesService[F],
    exchangeRatesRefreshTimestampRepo: ExchangeRatesRefreshTimestampRepository[D]
  )(implicit transact: D ~> F, logs: Logs[F, F]): F[DefaultExchangeRatesJobService[F, D]] =
    Logs[F, F].forService[DefaultExchangeRatesJobService[F, D]].map { implicit l =>
      new DefaultExchangeRatesJobService[F, D](exchangeRatesService, exchangeRatesRefreshTimestampRepo)
    }
}
