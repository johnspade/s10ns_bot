package ru.johnspade.s10ns.settings

import cats.effect.Sync
import cats.implicits._
import ru.johnspade.s10ns.bot.{DefCurrency, SettingsDialog, StateMessageService}
import ru.johnspade.s10ns.bot.engine.{DialogEngine, ReplyMessage}
import ru.johnspade.s10ns.bot.ValidatorNec._
import ru.johnspade.s10ns.bot.engine.TelegramOps.singleInlineKeyboardButton
import ru.johnspade.s10ns.user.User
import telegramium.bots.{InlineKeyboardMarkup, MarkupInlineKeyboard}

class SettingsService[F[_] : Sync](
  private val dialogEngine: DialogEngine[F],
  private val stateMessageService: StateMessageService[F]
) {
  def startDefaultCurrencyDialog(user: User): F[ReplyMessage] = {
    val start = SettingsDialogState.DefaultCurrency
    val dialog = SettingsDialog(state = start)
    stateMessageService.getMessage(start).flatMap {
      dialogEngine.startDialog(user, dialog, _)
    }
  }

  def saveDefaultCurrency(user: User, text: Option[String]): F[ValidationResult[ReplyMessage]] =
    validateText(text.map(_.trim.toUpperCase))
      .andThen(validateCurrency)
      .map { currency =>
        dialogEngine.resetAndCommit(user.copy(defaultCurrency = currency), "Default currency set")
      }
      .sequence

  val onSettingsCommand: F[ReplyMessage] =
    Sync[F].pure {
      ReplyMessage(
        "Settings",
        MarkupInlineKeyboard(InlineKeyboardMarkup(
          singleInlineKeyboardButton("Default currency", DefCurrency)
        )).some
      )
    }
}
