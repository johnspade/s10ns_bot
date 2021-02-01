package ru.johnspade.s10ns.bot

import ru.johnspade.s10ns.CbDataUserRoutes
import ru.johnspade.tgbot.callbackqueries.CallbackQueryDsl

trait CallbackQueryUserController[F[_]] extends CallbackQueryDsl {
  def routes: CbDataUserRoutes[F]
}
