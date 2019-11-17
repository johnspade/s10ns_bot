package ru.johnspade.s10ns.exchangerates

import java.time.LocalDate

case class ExchangeRates(timestamp: Long, date: LocalDate, rates: Map[String, BigDecimal])
