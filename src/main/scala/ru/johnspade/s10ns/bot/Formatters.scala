package ru.johnspade.s10ns.bot

import org.joda.money.format.MoneyFormatter
import org.joda.money.format.MoneyFormatterBuilder

object Formatters {
  val MoneyFormatter: MoneyFormatter =
    new MoneyFormatterBuilder()
      .appendAmount()
      .appendLiteral(" ")
      .appendCurrencySymbolLocalized()
      .toFormatter
}
