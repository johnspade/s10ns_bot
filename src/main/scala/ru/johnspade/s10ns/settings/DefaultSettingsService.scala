package ru.johnspade.s10ns.settings

import cats.Monad
import cats.implicits._
import cats.~>

import telegramium.bots.Markdown2

import ru.johnspade.s10ns.bot.Markup
import ru.johnspade.s10ns.bot.SettingsDialog
import ru.johnspade.s10ns.bot.ValidatorNec._
import ru.johnspade.s10ns.bot.engine.DialogEngine
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.engine.StateMessageService
import ru.johnspade.s10ns.user.User
import ru.johnspade.s10ns.user.UserRepository

class DefaultSettingsService[F[_]: Monad, D[_]: Monad](
    private val dialogEngine: DialogEngine[F],
    private val stateMessageService: StateMessageService[F, SettingsDialogState],
    private val userRepo: UserRepository[D]
)(private implicit val transact: D ~> F)
    extends SettingsService[F] {
  override def startDefaultCurrencyDialog(user: User): F[List[ReplyMessage]] = {
    val start  = SettingsDialogState.DefaultCurrency
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

  override def toggleNotifyByDefault(user: User): F[ReplyMessage] = {
    val updatedUser = user.copy(notifyByDefault = user.notifyByDefault.getOrElse(false).some.map(!_))
    transact(userRepo.update(updatedUser)) *> onSettingsCommand(updatedUser)
  }

  override def onSettingsCommand(user: User): F[ReplyMessage] =
    Monad[F].pure {
      val notifyByDefault = user.notifyByDefault match {
        case Some(true) => "YES"
        case _          => "NO"
      }
      ReplyMessage(
        s"""Default currency: *${user.defaultCurrency.getCode()}*
           |Enable notifications for new subscriptions by default: *$notifyByDefault*
           |""".stripMargin,
        Markup.SettingsMarkup.some,
        parseMode = Markdown2.some
      )
    }
}
