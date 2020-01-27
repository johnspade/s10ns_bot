package ru.johnspade.s10ns.exchangerates

import ru.johnspade.s10ns.DoobieRepositorySpec
import ru.johnspade.s10ns.exchangerates.DoobieExchangeRatesRepository.ExchangeRatesSql._

class DoobieExchangeRatesRepositorySpec extends DoobieRepositorySpec {
  test("get") {
    check(get())
  }

  test("save") {
    check(save)
  }

  test("clear") {
    check(clear())
  }
}
