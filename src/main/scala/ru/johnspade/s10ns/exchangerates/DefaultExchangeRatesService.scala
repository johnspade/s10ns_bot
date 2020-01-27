package ru.johnspade.s10ns.exchangerates

import java.time.Instant

import cats.effect.{Sync, Timer}
import cats.implicits._
import cats.{Monad, ~>}
import io.chrisdavenport.log4cats.Logger
import retry.CatsEffect._
import retry.RetryDetails.{GivingUp, WillDelayAndRetry}
import retry._

import scala.concurrent.duration._

class DefaultExchangeRatesService[F[_] : Sync : Logger : Timer, D[_] : Monad](
  private val fixerApi: FixerApi[F],
  private val exchangeRatesRepository: ExchangeRatesRepository[D],
  private val exchangeRatesRefreshTimestampRepo: ExchangeRatesRefreshTimestampRepository[D],
  private val cache: ExchangeRatesCache[F]
)(private implicit val transact: D ~> F) extends ExchangeRatesService[F] {
  private val retryPolicy = RetryPolicies.limitRetries[F](3) join RetryPolicies.exponentialBackoff[F](1.minute)

  override def saveRates(): F[Unit] = {
    def saveToDb(rates: ExchangeRates): F[Unit] =
      transact {
        exchangeRatesRepository.clear()
          .productR(exchangeRatesRepository.save(rates.rates))
          .productR(exchangeRatesRefreshTimestampRepo.save(Instant.ofEpochSecond(rates.timestamp)))
      }

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

  override def getRates: F[Map[String, BigDecimal]] = cache.get
}
