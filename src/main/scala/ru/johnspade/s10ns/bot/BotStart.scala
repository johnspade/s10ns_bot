package ru.johnspade.s10ns.bot

import cats.syntax.option._

import telegramium.bots.Html
import telegramium.bots.ReplyKeyboardMarkup
import telegramium.bots.high.keyboards.KeyboardButtons
import telegramium.bots.high.keyboards.ReplyKeyboardMarkups

import ru.johnspade.s10ns.bot.engine.ReplyMessage

object BotStart {
  val markup: ReplyKeyboardMarkup = ReplyKeyboardMarkups.singleColumn(
    List(
      KeyboardButtons.text("\uD83D\uDCCB List"),
      KeyboardButtons.text("➕ New subscription (default currency)"),
      KeyboardButtons.text("\uD83D\uDCB2 New subscription"),
      KeyboardButtons.text("⚙️ Settings")
    ),
    resizeKeyboard = Some(true)
  )

  private final val Help =
    """Manage your subscriptions and get detailed insights of your recurring expenses.
      |
      |Select your default currency: /settings. Enter a currency code manually if it's not on the list.
      |
      |<a href="https://buymeacoff.ee/johnspade">Buy me a coffee</a> ☕""".stripMargin

  val message: ReplyMessage = ReplyMessage(
    Help,
    markup = markup.some,
    parseMode = Html.some,
    disableWebPagePreview = true.some
  )
}
