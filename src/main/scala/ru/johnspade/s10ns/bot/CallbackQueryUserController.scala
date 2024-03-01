package ru.johnspade.s10ns.bot

import ru.johnspade.tgbot.callbackqueries.CallbackQueryDsl

import ru.johnspade.s10ns.CbDataUserRoutes

trait CallbackQueryUserController[F[_]] extends CallbackQueryDsl {
  def routes: CbDataUserRoutes[F]
}
