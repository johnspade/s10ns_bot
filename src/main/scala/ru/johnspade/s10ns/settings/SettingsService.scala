package ru.johnspade.s10ns.settings

import cats.effect.Sync
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import ru.johnspade.s10ns.common.ValidatorNec._
import ru.johnspade.s10ns.telegram.{CbData, ReplyMessage}
import ru.johnspade.s10ns.user.{DialogType, SettingsDialogState, User, UserRepository}
import telegramium.bots.{InlineKeyboardButton, InlineKeyboardMarkup, MarkupInlineKeyboard}

class SettingsService[F[_] : Sync](
  private val userRepository: UserRepository,
  private val xa: Transactor[F]
) {
  def startDefaultCurrencyDialog(user: User): F[ReplyMessage] =
    userRepository.createOrUpdate {
      user.copy(dialogType = DialogType.Settings.some, settingsDialogState = SettingsDialogState.DefaultCurrency.some)
    }
      .transact(xa)
      .map(_ => ReplyMessage("Default currency:"))

  def saveDefaultCurrency(user: User, text: Option[String]): F[ValidationResult[ReplyMessage]] = {
    validateText(text.map(_.trim.toUpperCase))
      .andThen(validateCurrency)
      .map { currency =>
        userRepository.update(user.copy(defaultCurrency = currency, dialogType = None))
          .transact(xa)
          .map(_ => ReplyMessage("Default currency set"))
      }
      .sequence
  }

  val onSettingsCommand: F[ReplyMessage] =
    Sync[F].pure {
      ReplyMessage(
        "Settings",
        MarkupInlineKeyboard(InlineKeyboardMarkup(
          List(List(InlineKeyboardButton("Default currency", callbackData = CbData.defaultCurrency.some)))
        )).some
      )
    }
}
