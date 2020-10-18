package ru.johnspade.s10ns.settings

import cats.Monad
import cats.effect.{Sync, Timer}
import cats.implicits._
import ru.johnspade.s10ns.CbDataUserRoutes
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.engine.TelegramOps.{ackCb, sendReplyMessages, toReplyMessage}
import ru.johnspade.s10ns.bot.engine.callbackqueries.CallbackQueryContextRoutes
import ru.johnspade.s10ns.bot.{CallbackQueryUserController, CbData, DefCurrency, Errors, SettingsDialog}
import ru.johnspade.s10ns.user.User
import telegramium.bots.Message
import telegramium.bots.high.Api
import tofu.logging.{Logging, Logs}

class SettingsController[F[_]: Sync: Logging: Timer](
  private val settingsService: SettingsService[F]
)(implicit bot: Api[F]) extends CallbackQueryUserController[F] {
  def message(user: User, dialog: SettingsDialog, message: Message): F[List[ReplyMessage]] =
    (dialog.state match {
      case SettingsDialogState.DefaultCurrency =>
        settingsService.saveDefaultCurrency(user, message.text).map(toReplyMessage).some
      case _ => Option.empty[F[ReplyMessage]]
    }).getOrElse(Sync[F].pure(ReplyMessage(Errors.Default)))
      .map(List(_))

  override val routes: CbDataUserRoutes[F] = CallbackQueryContextRoutes.of[CbData, User, F] {
    case DefCurrency in cb as user =>
      ackCb(cb) *> cb.message.map { msg =>
        settingsService.startDefaultCurrencyDialog(user)
          .flatMap(sendReplyMessages(msg, _).void)
      }.getOrElse(Monad[F].unit)
  }

  val settingsCommand: F[ReplyMessage] = settingsService.onSettingsCommand
}

object SettingsController {
  def apply[F[_]: Sync: Timer](
    settingsService: SettingsService[F]
  )(implicit bot: Api[F], logs: Logs[F, F]): F[SettingsController[F]] =
    logs.forService[SettingsController[F]].map { implicit l =>
      new SettingsController[F](settingsService)
    }
}
