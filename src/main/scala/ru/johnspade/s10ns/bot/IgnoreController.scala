package ru.johnspade.s10ns.bot

import cats.effect.Sync
import ru.johnspade.s10ns.CbDataRoutes
import ru.johnspade.s10ns.bot.engine.TelegramOps.ackCb
import ru.johnspade.s10ns.bot.engine.callbackqueries.CallbackQueryRoutes
import telegramium.bots.high.Api

class IgnoreController[F[_]: Sync](implicit bot: Api[F]) extends CallbackQueryController[F] {
  override def routes: CbDataRoutes[F] = CallbackQueryRoutes.of[CbData, F] {
    case Ignore in cb => ackCb[F](cb)
  }
}
