package ru.johnspade.s10ns.help

import ru.johnspade.s10ns.telegram.ReplyMessage
import cats.implicits._
import telegramium.bots.{KeyboardButton, MarkupReplyKeyboard, ReplyKeyboardMarkup}

object BotStart {
  val markup: MarkupReplyKeyboard = MarkupReplyKeyboard(ReplyKeyboardMarkup(
    List(
      List(KeyboardButton("\uD83D\uDCCB List")),
      List(KeyboardButton("➕ Create subscription (default currency)")),
      List(KeyboardButton("\uD83D\uDCB2 Create subscription"))
    ),
    resizeKeyboard = Some(true)
  ))

  val message: ReplyMessage = ReplyMessage("Hello from s10ns_bot!", markup = markup.some)
}
