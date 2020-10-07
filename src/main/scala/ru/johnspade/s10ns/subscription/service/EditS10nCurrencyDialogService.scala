package ru.johnspade.s10ns.subscription.service

import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.{EditS10nCurrency, EditS10nCurrencyDialog}
import ru.johnspade.s10ns.user.User

trait EditS10nCurrencyDialogService[F[_]] {
  def onEditS10nCurrencyCb(user: User, data: EditS10nCurrency): F[List[ReplyMessage]]

  def saveCurrency(user: User, dialog: EditS10nCurrencyDialog, text: Option[String]): F[RepliesValidated]

  def saveAmount(user: User, dialog: EditS10nCurrencyDialog, text: Option[String]): F[RepliesValidated]
}
