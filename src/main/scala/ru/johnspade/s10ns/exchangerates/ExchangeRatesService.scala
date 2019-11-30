package ru.johnspade.s10ns.exchangerates

import java.time.Instant

import cats.effect.{Sync, Timer}
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.chrisdavenport.log4cats.Logger
import retry.CatsEffect._
import retry.RetryDetails.{GivingUp, WillDelayAndRetry}
import retry._
import ru.johnspade.s10ns.money.{ExchangeRatesRefreshTimestampRepository, ExchangeRatesRepository}

import scala.concurrent.duration._

class ExchangeRatesService[F[_] : Sync : Logger : Timer](
  private val fixerApi: FixerApi[F],
  private val exchangeRatesRepository: ExchangeRatesRepository,
  private val exchangeRatesRefreshTimestampRepo: ExchangeRatesRefreshTimestampRepository,
  private val cache: ExchangeRatesCache[F]
)(private implicit val xa: Transactor[F]) {
  private val retryPolicy = RetryPolicies.limitRetries[F](3) join RetryPolicies.exponentialBackoff[F](1.minute)

  def saveRates(): F[Unit] = {
    def saveToDb(rates: ExchangeRates): F[Unit] =
      exchangeRatesRepository.clear()
        .productR(exchangeRatesRepository.save(rates.rates))
        .productR(exchangeRatesRefreshTimestampRepo.save(Instant.ofEpochSecond(rates.timestamp)))
        .transact(xa)

    def logError(err: Throwable, details: RetryDetails): F[Unit] = details match {
      case WillDelayAndRetry(_, retriesSoFar: Int, _) =>
        Logger[F].error(s"Failed to get rates. So far we have retried $retriesSoFar times.")
      case GivingUp(totalRetries: Int, _) =>
        Logger[F].error(s"Giving up after $totalRetries retries. Last error: $err")
    }

    retryingOnAllErrors[Unit](
      policy = retryPolicy,
      onError = logError
    ) {
      fixerApi.getLatestRates
        .flatMap { rates =>
          saveToDb(rates) *> cache.set(rates.rates)
        }
    }.handleError(_ => ())
  }

  def getRates: F[Map[String, BigDecimal]] = cache.get
}
