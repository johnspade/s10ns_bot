package ru.johnspade.s10ns.bot.engine

import cats.Monad

class DefaultMsgService[F[_]: Monad, S <: DialogState] extends StateMessageService[F, S]
