package ru.johnspade.s10ns.settings
import ru.johnspade.s10ns.bot.ValidatorNec
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.user.User

trait SettingsService[F[_]] {
  def startDefaultCurrencyDialog(user: User): F[ReplyMessage]

  def saveDefaultCurrency(user: User, text: Option[String]): F[ValidatorNec.ValidationResult[ReplyMessage]]

  def onSettingsCommand: F[ReplyMessage]
}
