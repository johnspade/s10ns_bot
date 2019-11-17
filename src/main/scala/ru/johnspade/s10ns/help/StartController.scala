package ru.johnspade.s10ns.help

import cats.effect.Sync
import cats.implicits._
import ru.johnspade.s10ns.telegram.ReplyMessage
import ru.johnspade.s10ns.user.User

class StartController[F[_] : Sync](
  private val startService: StartService[F]
) {
  private val helpMessage = Sync[F].pure(ReplyMessage("Hello from s10ns_bot!", StartMarkup.markup.some))

  def startCommand(from: User): F[ReplyMessage] = startService.reset(from) *> helpMessage

  val helpCommand: F[ReplyMessage] = helpMessage
}
