package ru.johnspade.s10ns.help

import telegramium.bots.{KeyboardButton, MarkupReplyKeyboard, ReplyKeyboardMarkup}

object StartMarkup {
  val markup = MarkupReplyKeyboard(ReplyKeyboardMarkup(
    List(
      List(KeyboardButton("\uD83D\uDCCB List")),
      List(KeyboardButton("➕ Create subscription (default currency)")),
      List(KeyboardButton("\uD83D\uDCB2 Create subscription"))
    ),
    resizeKeyboard = Some(true)
  ))
}
