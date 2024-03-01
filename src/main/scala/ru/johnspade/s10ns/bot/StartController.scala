package ru.johnspade.s10ns.bot

import cats.Applicative

import ru.johnspade.s10ns.bot.engine.DialogEngine
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.user.User

class StartController[F[_]: Applicative](
    private val dialogEngine: DialogEngine[F]
) {
  def startCommand(from: User): F[ReplyMessage] = dialogEngine.sayHi(from)

  val helpCommand: F[ReplyMessage] = Applicative[F].pure(BotStart.message)
}
