package ru.johnspade.s10ns.telegram

import telegramium.bots.KeyboardMarkup

case class ReplyMessage(text: String, markup: Option[KeyboardMarkup] = None)
