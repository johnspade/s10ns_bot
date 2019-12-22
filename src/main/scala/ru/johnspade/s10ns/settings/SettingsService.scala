package ru.johnspade.s10ns.settings

import cats.effect.Sync
import cats.implicits._
import ru.johnspade.s10ns.common.ValidatorNec._
import ru.johnspade.s10ns.telegram.TelegramOps.singleInlineKeyboardButton
import ru.johnspade.s10ns.telegram.{DefCurrency, DialogEngine, ReplyMessage}
import ru.johnspade.s10ns.user.{SettingsDialog, SettingsDialogState, User}
import telegramium.bots.{InlineKeyboardMarkup, MarkupInlineKeyboard}

class SettingsService[F[_] : Sync](
  private val dialogEngine: DialogEngine[F]
) {
  def startDefaultCurrencyDialog(user: User): F[ReplyMessage] = {
    val dialog = SettingsDialog(state = SettingsDialogState.DefaultCurrency)
    dialogEngine.startDialog(user, dialog, ReplyMessage("Default currency:"))
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
