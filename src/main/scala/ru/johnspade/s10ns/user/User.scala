package ru.johnspade.s10ns.user

import org.joda.money.CurrencyUnit

import ru.johnspade.s10ns.bot.Dialog

case class User(
    id: Long,
    firstName: String,
    chatId: Option[Long],
    defaultCurrency: CurrencyUnit = CurrencyUnit.EUR,
    dialog: Option[Dialog] = None,
    notifyByDefault: Option[Boolean] = None
)
