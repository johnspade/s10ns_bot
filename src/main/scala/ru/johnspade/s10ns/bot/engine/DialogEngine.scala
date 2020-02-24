package ru.johnspade.s10ns.bot.engine

import ru.johnspade.s10ns.bot.Dialog
import ru.johnspade.s10ns.user.User

trait DialogEngine[F[_]] {
  def startDialog(user: User, dialog: Dialog, message: ReplyMessage): F[List[ReplyMessage]]

  def resetAndCommit(user: User, message: String): F[ReplyMessage]

  def sayHi(user: User): F[ReplyMessage]
}
