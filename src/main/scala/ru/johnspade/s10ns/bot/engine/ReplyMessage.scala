package ru.johnspade.s10ns.bot.engine

import telegramium.bots.KeyboardMarkup

case class ReplyMessage(text: String, markup: Option[KeyboardMarkup] = None)
