package ru.johnspade.s10ns.subscription.service

import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.{EditS10nAmount, EditS10nAmountDialog}
import ru.johnspade.s10ns.user.User
import telegramium.bots.CallbackQuery

trait EditS10nAmountDialogService[F[_]] {
  def saveAmount(user: User, dialog: EditS10nAmountDialog, text: Option[String]): F[RepliesValidated]

  def onEditS10nAmountCb(user: User, cb: CallbackQuery, data: EditS10nAmount): F[List[ReplyMessage]]
}
