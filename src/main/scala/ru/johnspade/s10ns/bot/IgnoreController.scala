package ru.johnspade.s10ns.bot

import cats.effect.Sync

import ru.johnspade.tgbot.callbackqueries.CallbackQueryRoutes
import telegramium.bots.high.Api

import ru.johnspade.s10ns.CbDataRoutes
import ru.johnspade.s10ns.bot.engine.TelegramOps.ackCb

class IgnoreController[F[_]: Sync](implicit bot: Api[F]) extends CallbackQueryController[F] {
  override def routes: CbDataRoutes[F] = CallbackQueryRoutes.of {
    case Ignore in cb => ackCb(cb)
  }
}
