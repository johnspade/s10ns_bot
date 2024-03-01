package ru.johnspade.s10ns.exchangerates

import cats.effect.IO

class InMemoryExchangeRatesStorage extends ExchangeRatesStorage[IO] {
  override val getRates: IO[Map[String, BigDecimal]] = IO(
    Map(
      "EUR" -> BigDecimal(1),
      "RUB" -> BigDecimal(69.479142),
      "USD" -> BigDecimal(1.116732),
      "RWF" -> BigDecimal(1055.766139)
    )
  )
}
