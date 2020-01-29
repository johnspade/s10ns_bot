package ru.johnspade.s10ns.exchangerates

import java.time.Instant

import cats.Id
import cats.effect.{IO, Timer}
import com.softwaremill.diffx.scalatest.DiffMatcher
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ru.johnspade.s10ns.TestTransactor.transact

import scala.concurrent.ExecutionContext

class DefaultExchangeRatesServiceSpec extends AnyFlatSpec with Matchers with OptionValues with DiffMatcher {
  private implicit val logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.create[IO].unsafeRunSync
  private implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

  private val timestamp = Instant.now.getEpochSecond
  private val fixerApi = new InMemoryFixerApi(timestamp)
  private val exchangeRatesRepo = new InMemoryExchangeRatesRepository
  private val exchangeRatesRefreshTimestampRepo = new InMemoryExchangeRatesRefreshTimestampRepository

  private val sampleRates = Map("RUB" -> BigDecimal(30))

  "getRates" should "return rates" in {
    val exchangeRatesService = new DefaultExchangeRatesService[IO, Id](
      fixerApi,
      exchangeRatesRepo,
      exchangeRatesRefreshTimestampRepo,
      ExchangeRatesCache.create[IO](sampleRates).unsafeRunSync
    )

    exchangeRatesService.getRates.unsafeRunSync should matchTo(sampleRates)
  }

  "saveRates" should "update rates" in {
    val cache = ExchangeRatesCache.create[IO](sampleRates).unsafeRunSync
    val exchangeRatesService = new DefaultExchangeRatesService[IO, Id](
      fixerApi,
      exchangeRatesRepo,
      exchangeRatesRefreshTimestampRepo,
      cache
    )
    exchangeRatesService.saveRates().unsafeRunSync

    val expectedRates = fixerApi.getLatestRates.unsafeRunSync.rates

    exchangeRatesRepo.get() should matchTo(expectedRates)
    cache.get.unsafeRunSync should matchTo(expectedRates)
    exchangeRatesRefreshTimestampRepo.get().value.getEpochSecond shouldBe timestamp
  }

  // todo test errors and retries
}
