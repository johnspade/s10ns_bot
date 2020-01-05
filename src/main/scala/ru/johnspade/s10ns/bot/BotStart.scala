package ru.johnspade.s10ns.bot

import cats.implicits._
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import telegramium.bots.{KeyboardButton, MarkupReplyKeyboard, ReplyKeyboardMarkup}

object BotStart {
  val markup: MarkupReplyKeyboard = MarkupReplyKeyboard(ReplyKeyboardMarkup(
    List( // todo Settings
      List(KeyboardButton("\uD83D\uDCCB List")),
      List(KeyboardButton("➕ Create subscription (default currency)")),
      List(KeyboardButton("\uD83D\uDCB2 Create subscription"))
    ),
    resizeKeyboard = Some(true)
  ))

  val message: ReplyMessage = ReplyMessage("Hello from s10ns_bot!", markup = markup.some)
}
