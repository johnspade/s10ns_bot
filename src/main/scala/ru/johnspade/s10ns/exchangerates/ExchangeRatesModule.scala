package ru.johnspade.s10ns.exchangerates

import cats.effect.{Async, Concurrent, Temporal}
import cats.implicits._
import cats.~>
import doobie.free.connection.ConnectionIO
import retry.RetryPolicies
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import tofu.logging.Logs

import scala.concurrent.duration._

class ExchangeRatesModule[F[_], D[_]] private (
  val fixerApi: FixerApi[F],
  val exchangeRatesRepository: ExchangeRatesRepository[D],
  val exchangeRatesRefreshTimestampRepository: ExchangeRatesRefreshTimestampRepository[D],
  val exchangeRatesCache: ExchangeRatesCache[F],
  val exchangeRatesService: ExchangeRatesService[F],
  val exchangeRatesJobService: ExchangeRatesJobService[F]
)

object ExchangeRatesModule {
  def make[F[_]: Concurrent: Temporal: Async](
    fixerToken: String
  )(implicit transact: ConnectionIO ~> F, logs: Logs[F, F]): F[ExchangeRatesModule[F, ConnectionIO]] = {
    val exchangeRatesRepo: ExchangeRatesRepository[ConnectionIO] = new DoobieExchangeRatesRepository
    val exchangeRatesRefreshTimestampRepo: ExchangeRatesRefreshTimestampRepository[ConnectionIO] =
      new DoobieExchangeRatesRefreshTimestampRepository
    val retryPolicy = RetryPolicies.limitRetries[F](3) join RetryPolicies.exponentialBackoff[F](1.minute)
    for {
      sttpBackend <- AsyncHttpClientCatsBackend[F]()
      fixerApi = new FixerApiInterpreter[F](fixerToken, sttpBackend)
      exchangeRates <- transact(exchangeRatesRepo.get())
      exchangeRatesCache <- ExchangeRatesCache.create[F](exchangeRates)
      exchangeRatesService <- DefaultExchangeRatesService(
        fixerApi,
        exchangeRatesRepo,
        exchangeRatesRefreshTimestampRepo,
        exchangeRatesCache,
        retryPolicy
      )
      exchangeRatesJobService <- DefaultExchangeRatesJobService(
        exchangeRatesService,
        exchangeRatesRefreshTimestampRepo
      )
    } yield new ExchangeRatesModule[F, ConnectionIO](
      fixerApi = fixerApi,
      exchangeRatesRepository = exchangeRatesRepo,
      exchangeRatesRefreshTimestampRepository = exchangeRatesRefreshTimestampRepo,
      exchangeRatesCache = exchangeRatesCache,
      exchangeRatesService = exchangeRatesService,
      exchangeRatesJobService = exchangeRatesJobService
    )
  }
}
