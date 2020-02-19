package ru.johnspade.s10ns.exchangerates

import java.time.{LocalDate, ZoneOffset}

import cats.effect.IO

class InMemoryFixerApi(timestamp: Long) extends FixerApi[IO] {
  override val getLatestRates: IO[ExchangeRates] = IO {
    ExchangeRates(
      timestamp,
      LocalDate.now(ZoneOffset.UTC),
      Map(
        "EUR" -> BigDecimal(1),
        "RUB" -> BigDecimal(69.479142),
        "USD" -> BigDecimal(1.116732),
        "RWF" -> BigDecimal(1055.766139)
      )
    )
  }
}
