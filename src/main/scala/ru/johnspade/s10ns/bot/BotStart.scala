package ru.johnspade.s10ns.bot

import cats.implicits._
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import telegramium.bots.{KeyboardButton, ReplyKeyboardMarkup}

object BotStart {
  val markup: ReplyKeyboardMarkup = ReplyKeyboardMarkup(
    List(
      List(KeyboardButton("\uD83D\uDCCB List")),
      List(KeyboardButton("➕ New subscription (default currency)")),
      List(KeyboardButton("\uD83D\uDCB2 New subscription")),
      List(KeyboardButton("⚙️ Settings"))
    ),
    resizeKeyboard = Some(true)
  )

  private final val Help = "Manage your subscriptions and get detailed insights of your recurring expenses."

  val message: ReplyMessage = ReplyMessage(Help, markup = markup.some)
}
