package ru.johnspade.s10ns.settings

import ru.johnspade.s10ns.bot.ValidatorNec.ValidationResult
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.user.User

trait SettingsService[F[_]] {
  def startDefaultCurrencyDialog(user: User): F[List[ReplyMessage]]

  def saveDefaultCurrency(user: User, text: Option[String]): F[ValidationResult[ReplyMessage]]

  def onSettingsCommand: F[ReplyMessage]
}
