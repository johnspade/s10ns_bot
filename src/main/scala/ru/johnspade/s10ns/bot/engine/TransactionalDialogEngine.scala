package ru.johnspade.s10ns.bot.engine

import ru.johnspade.s10ns.user.User

trait TransactionalDialogEngine[F[_], D[_]] extends DialogEngine[F] {
  def reset(user: User, message: String): D[ReplyMessage]
}
