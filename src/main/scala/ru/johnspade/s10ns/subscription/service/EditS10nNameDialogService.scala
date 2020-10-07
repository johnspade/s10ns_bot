package ru.johnspade.s10ns.subscription.service

import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.{EditS10nName, EditS10nNameDialog}
import ru.johnspade.s10ns.user.User

trait EditS10nNameDialogService[F[_]] {
  def onEditS10nNameCb(user: User, data: EditS10nName): F[List[ReplyMessage]]

  def saveName(user: User, dialog: EditS10nNameDialog, text: Option[String]): F[RepliesValidated]
}
