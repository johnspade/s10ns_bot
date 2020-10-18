package ru.johnspade.s10ns.bot.engine.callbackqueries

final case class ContextCallbackQuery[I, A](context: A, query: CallbackQueryData[I])
