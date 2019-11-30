package ru.johnspade.s10ns.user

import org.joda.money.CurrencyUnit
import ru.johnspade.s10ns.user.tags._

case class User(
  id: UserId,
  firstName: FirstName,
  chatId: Option[ChatId],
  defaultCurrency: CurrencyUnit = CurrencyUnit.EUR,
  dialog: Option[Dialog]
)
