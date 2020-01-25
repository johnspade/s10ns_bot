package ru.johnspade.s10ns.bot

import cats.effect.IO
import org.joda.money.{CurrencyUnit, Money}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ru.johnspade.s10ns.exchangerates.ExchangeRatesStorage
import cats.implicits._

class MoneyServiceSpec extends AnyFlatSpec with Matchers {
  private val exchangeRatesStorage: ExchangeRatesStorage[IO] = new ExchangeRatesStorage[IO] {
    override val getRates: IO[Map[String, BigDecimal]] = IO(Map(
      "EUR" -> BigDecimal(1),
      "RUB" -> BigDecimal(69.479142),
      "USD" -> BigDecimal(1.116732),
      "RWF" -> BigDecimal(1055.766139)
    ))
  }
  private val moneyService = new MoneyService[IO](exchangeRatesStorage)

  "convert" should "convert between currencies correctly" in {
    val amount = Money.of(CurrencyUnit.USD, BigDecimal(15).bigDecimal)
    moneyService.convert(amount, CurrencyUnit.EUR).unsafeRunSync shouldBe
      Money.of(CurrencyUnit.EUR, BigDecimal(13.43).bigDecimal).some
  }
}
