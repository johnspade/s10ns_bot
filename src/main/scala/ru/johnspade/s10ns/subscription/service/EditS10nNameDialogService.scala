package ru.johnspade.s10ns.subscription.service

import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.{EditS10nName, EditS10nNameDialog}
import ru.johnspade.s10ns.user.User
import telegramium.bots.CallbackQuery

trait EditS10nNameDialogService[F[_]] {
  def onEditS10nNameCb(user: User, cb: CallbackQuery, data: EditS10nName): F[List[ReplyMessage]]

  def saveName(user: User, dialog: EditS10nNameDialog, text: Option[String]): F[RepliesValidated]
}
