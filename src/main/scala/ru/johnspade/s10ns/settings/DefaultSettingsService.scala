package ru.johnspade.s10ns.settings

import cats.effect.Sync
import cats.implicits._
import ru.johnspade.s10ns.bot.ValidatorNec._
import ru.johnspade.s10ns.bot.engine.TelegramOps.inlineKeyboardButton
import ru.johnspade.s10ns.bot.engine.{DialogEngine, ReplyMessage, StateMessageService}
import ru.johnspade.s10ns.bot.{DefCurrency, SettingsDialog}
import ru.johnspade.s10ns.user.User
import telegramium.bots.high.keyboards.InlineKeyboardMarkups

class DefaultSettingsService[F[_] : Sync](
  private val dialogEngine: DialogEngine[F],
  private val stateMessageService: StateMessageService[F, SettingsDialogState]
) extends SettingsService[F] {
  override def startDefaultCurrencyDialog(user: User): F[List[ReplyMessage]] = {
    val start = SettingsDialogState.DefaultCurrency
    val dialog = SettingsDialog(state = start)
    stateMessageService.createReplyMessage(start).flatMap(dialogEngine.startDialog(user, dialog, _))
  }

  override def saveDefaultCurrency(user: User, text: Option[String]): F[ValidationResult[ReplyMessage]] =
    validateText(text.map(_.trim.toUpperCase))
      .andThen(validateCurrency)
      .map { currency =>
        dialogEngine.resetAndCommit(user.copy(defaultCurrency = currency), "Default currency set")
      }
      .sequence

  override val onSettingsCommand: F[ReplyMessage] =
    Sync[F].pure {
      ReplyMessage(
        "Settings",
        InlineKeyboardMarkups.singleButton(inlineKeyboardButton("Default currency", DefCurrency)).some
      )
    }
}
