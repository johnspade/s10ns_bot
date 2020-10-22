package ru.johnspade.s10ns.bot.engine

import telegramium.bots.{KeyboardMarkup, ParseMode}

case class ReplyMessage(
  text: String,
  markup: Option[KeyboardMarkup] = None,
  parseMode: Option[ParseMode] = None,
  disableWebPagePreview: Option[Boolean] = Option.empty
)
