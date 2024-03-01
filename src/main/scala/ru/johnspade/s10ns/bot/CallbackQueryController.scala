package ru.johnspade.s10ns.bot

import ru.johnspade.tgbot.callbackqueries.CallbackQueryDsl

import ru.johnspade.s10ns.CbDataRoutes

trait CallbackQueryController[F[_]] extends CallbackQueryDsl {
  def routes: CbDataRoutes[F]
}
