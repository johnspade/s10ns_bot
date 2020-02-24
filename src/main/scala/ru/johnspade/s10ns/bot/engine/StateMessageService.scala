package ru.johnspade.s10ns.bot.engine

import cats.Monad

abstract class StateMessageService[F[_] : Monad, S <: DialogState] {
  def createReplyMessage(state: S): F[ReplyMessage] = Monad[F].pure {
    ReplyMessage(text = state.message, markup = state.markup)
  }
}
