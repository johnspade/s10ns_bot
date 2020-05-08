package ru.johnspade.s10ns.exchangerates

import java.time.Instant

import cats.Id
import cats.effect.{IO, Timer}
import com.softwaremill.diffx.scalatest.DiffMatcher
import org.scalamock.scalatest.MockFactory
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import retry.RetryPolicies
import ru.johnspade.s10ns.TestTransactor.transact
import tofu.logging.Logs

import scala.concurrent.ExecutionContext

class DefaultExchangeRatesServiceSpec extends AnyFlatSpec with Matchers with OptionValues with DiffMatcher with MockFactory {
  private implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  private implicit val logs: Logs[IO, IO] = Logs.sync[IO, IO]

  private val timestamp = Instant.now.getEpochSecond
  private val fixerApi = new InMemoryFixerApi(timestamp)
  private val exchangeRatesRepo = new InMemoryExchangeRatesRepository
  private val exchangeRatesRefreshTimestampRepo = new InMemoryExchangeRatesRefreshTimestampRepository

  private val sampleRates = Map("RUB" -> BigDecimal(30))

  "getRates" should "return rates" in {
    val exchangeRatesService = DefaultExchangeRatesService[IO, Id](
      fixerApi,
      exchangeRatesRepo,
      exchangeRatesRefreshTimestampRepo,
      ExchangeRatesCache.create[IO](sampleRates).unsafeRunSync,
      RetryPolicies.alwaysGiveUp
    ).unsafeRunSync

    exchangeRatesService.getRates.unsafeRunSync should matchTo(sampleRates)
  }

  "saveRates" should "update rates" in {
    val cache = ExchangeRatesCache.create[IO](sampleRates).unsafeRunSync
    val exchangeRatesService = DefaultExchangeRatesService[IO, Id](
      fixerApi,
      exchangeRatesRepo,
      exchangeRatesRefreshTimestampRepo,
      cache,
      RetryPolicies.alwaysGiveUp
    ).unsafeRunSync
    exchangeRatesService.saveRates().unsafeRunSync

    val expectedRates = fixerApi.getLatestRates.unsafeRunSync.rates

    exchangeRatesRepo.get() should matchTo(expectedRates)
    cache.get.unsafeRunSync should matchTo(expectedRates)
    exchangeRatesRefreshTimestampRepo.get().value.getEpochSecond shouldBe timestamp
  }

  it should "retry on errors" in {
    val mockFixerApi = stub[FixerApi[IO]]
    val exchangeRatesService = DefaultExchangeRatesService[IO, Id](
      mockFixerApi,
      exchangeRatesRepo,
      exchangeRatesRefreshTimestampRepo,
      ExchangeRatesCache.create[IO](sampleRates).unsafeRunSync,
      RetryPolicies.limitRetries(2)
    ).unsafeRunSync

    (() => mockFixerApi.getLatestRates).when().throws(new RuntimeException())

    exchangeRatesService.saveRates().unsafeRunSync
    (() => mockFixerApi.getLatestRates).verify().repeat(3)
  }
}
