package ru.johnspade.s10ns.exchangerates

import java.time.Instant

import ru.johnspade.s10ns.DoobieRepositorySpec
import ru.johnspade.s10ns.exchangerates.DoobieExchangeRatesRefreshTimestampRepository.ExchangeRatesRefreshTimestampSql._

class DoobieExchangeRatesRefreshTimestampRepositorySpec extends DoobieRepositorySpec {
  test("get") {
    check(get())
  }

  test("save") {
    check(save(Instant.now))
  }
}
