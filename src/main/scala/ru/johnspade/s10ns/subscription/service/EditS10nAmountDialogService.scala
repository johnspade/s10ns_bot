package ru.johnspade.s10ns.subscription.service

import ru.johnspade.s10ns.bot.EditS10nAmount
import ru.johnspade.s10ns.bot.EditS10nAmountDialog
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.user.User

trait EditS10nAmountDialogService[F[_]] {
  def saveAmount(user: User, dialog: EditS10nAmountDialog, text: Option[String]): F[RepliesValidated]

  def onEditS10nAmountCb(user: User, data: EditS10nAmount): F[List[ReplyMessage]]
}
