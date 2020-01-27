package ru.johnspade.s10ns.bot

import cats.effect.Sync
import ru.johnspade.s10ns.bot.engine.{DialogEngine, ReplyMessage}
import ru.johnspade.s10ns.user.User

class StartController[F[_] : Sync](
  private val dialogEngine: DialogEngine[F]
) {
  def startCommand(from: User): F[ReplyMessage] = dialogEngine.sayHi(from)

  val helpCommand: F[ReplyMessage] = Sync[F].pure(BotStart.message)
}
