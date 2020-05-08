package ru.johnspade.s10ns.exchangerates

import java.time.Instant

import cats.effect.{Sync, Timer}
import cats.implicits._
import cats.{Monad, ~>}
import retry.CatsEffect._
import retry.RetryDetails.{GivingUp, WillDelayAndRetry}
import retry._
import tofu.logging._
import tofu.syntax.logging._

class DefaultExchangeRatesService[F[_]: Sync: Logging: Timer, D[_]: Monad](
  private val fixerApi: FixerApi[F],
  private val exchangeRatesRepository: ExchangeRatesRepository[D],
  private val exchangeRatesRefreshTimestampRepo: ExchangeRatesRefreshTimestampRepository[D],
  private val cache: ExchangeRatesCache[F],
  private val retryPolicy: RetryPolicy[F]
)(private implicit val transact: D ~> F) extends ExchangeRatesService[F] {

  override def saveRates(): F[Unit] = {
    def saveToDb(rates: ExchangeRates): F[Unit] =
      transact {
        exchangeRatesRepository.clear()
          .productR(exchangeRatesRepository.save(rates.rates))
          .productR(exchangeRatesRefreshTimestampRepo.save(Instant.ofEpochSecond(rates.timestamp)))
      }

    def logError(err: Throwable, details: RetryDetails): F[Unit] = details match {
      case WillDelayAndRetry(_, retriesSoFar: Int, _) =>
        error"Failed to get rates. So far we have retried $retriesSoFar times."
      case GivingUp(totalRetries: Int, _) =>
        error"Giving up after $totalRetries retries. Last error: ${err.toString}"
    }

    retryingOnAllErrors[Unit](
      policy = retryPolicy,
      onError = logError
    ) {
      for {
        _ <- info"Refreshing rates"
        rates <- fixerApi.getLatestRates
        _ <- saveToDb(rates) *> cache.set(rates.rates)
      } yield ()
    }.handleError(_ => ())
  }

  override def getRates: F[Map[String, BigDecimal]] = cache.get
}

object DefaultExchangeRatesService {
  def apply[F[_]: Sync: Timer, D[_]: Monad](
    fixerApi: FixerApi[F],
    exchangeRatesRepository: ExchangeRatesRepository[D],
    exchangeRatesRefreshTimestampRepo: ExchangeRatesRefreshTimestampRepository[D],
    cache: ExchangeRatesCache[F],
    retryPolicy: RetryPolicy[F]
  )(implicit transact: D ~> F, logs: Logs[F, F]): F[DefaultExchangeRatesService[F, D]] =
    logs.forService[DefaultExchangeRatesService[F, D]].map { implicit l =>
      new DefaultExchangeRatesService[F, D](
        fixerApi,
        exchangeRatesRepository,
        exchangeRatesRefreshTimestampRepo,
        cache,
        retryPolicy
      )
    }
}
