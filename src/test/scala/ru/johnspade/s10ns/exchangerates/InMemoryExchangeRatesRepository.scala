package ru.johnspade.s10ns.exchangerates

import cats.Id

import scala.collection.concurrent.TrieMap

class InMemoryExchangeRatesRepository extends ExchangeRatesRepository[Id] {
  val rates: TrieMap[String, BigDecimal] = TrieMap.empty

  override def save(newRates: Map[String, BigDecimal]): Unit = {
    rates.clear()
    newRates.foreach(rates += _)
  }

  override def get(): Map[String, BigDecimal] = rates.toMap

  override def clear(): Unit = rates.clear()
}
