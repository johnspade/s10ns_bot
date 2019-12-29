package ru.johnspade.s10ns.settings

import cats.Monad
import cats.effect.Sync
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import ru.johnspade.s10ns.common.Errors
import ru.johnspade.s10ns.telegram.ReplyMessage
import ru.johnspade.s10ns.telegram.TelegramOps.{ackCb, sendReplyMessage, toReplyMessage}
import ru.johnspade.s10ns.user.{SettingsDialog, SettingsDialogState, User}
import telegramium.bots.client.Api
import telegramium.bots.{CallbackQuery, Message}

class SettingsController[F[_] : Sync : Logger](
  private val settingsService: SettingsService[F]
) {
  def message(user: User, dialog: SettingsDialog, message: Message): F[List[ReplyMessage]] =
    (dialog.state match {
      case SettingsDialogState.DefaultCurrency =>
        settingsService.saveDefaultCurrency(user, message.text).map(toReplyMessage).some
      case _ => Option.empty[F[ReplyMessage]]
    }).getOrElse(Sync[F].pure(ReplyMessage(Errors.default)))
      .map(List(_))

  def defaultCurrencyCb(user: User, cb: CallbackQuery)(implicit bot: Api[F]): F[Unit] =
    ackCb(cb) *> cb.message.map { msg =>
      settingsService.startDefaultCurrencyDialog(user)
        .flatMap(sendReplyMessage(msg, _))
    }.getOrElse(Monad[F].unit)

  val settingsCommand: F[ReplyMessage] = settingsService.onSettingsCommand
}
