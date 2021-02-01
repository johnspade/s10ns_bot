package ru.johnspade.s10ns.bot

import ru.johnspade.s10ns.CbDataRoutes
import ru.johnspade.tgbot.callbackqueries.CallbackQueryDsl

trait CallbackQueryController[F[_]] extends CallbackQueryDsl {
  def routes: CbDataRoutes[F]
}
