package ru.johnspade.s10ns.bot

import cats.syntax.option._
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import telegramium.bots.high._

object BotStart {
  val markup: ReplyKeyboardMarkup = ReplyKeyboardMarkup.singleColumn(
    List(
      KeyboardButton.text("\uD83D\uDCCB List"),
      KeyboardButton.text("➕ New subscription (default currency)"),
      KeyboardButton.text("\uD83D\uDCB2 New subscription"),
      KeyboardButton.text("⚙️ Settings")
    ),
    resizeKeyboard = Some(true)
  )

  private final val Help = "Manage your subscriptions and get detailed insights of your recurring expenses."

  val message: ReplyMessage = ReplyMessage(Help, markup = markup.some)
}
