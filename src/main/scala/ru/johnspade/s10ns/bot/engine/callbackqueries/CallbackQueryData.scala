package ru.johnspade.s10ns.bot.engine.callbackqueries

import telegramium.bots.CallbackQuery

final case class CallbackQueryData[I](
  data: I,
  cb: CallbackQuery
)
