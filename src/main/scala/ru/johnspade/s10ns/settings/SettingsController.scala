package ru.johnspade.s10ns.settings

import cats.Defer
import cats.Monad
import cats.effect.Temporal
import cats.implicits._

import ru.johnspade.tgbot.callbackqueries.CallbackQueryContextRoutes
import telegramium.bots.ChatIntId
import telegramium.bots.InlineKeyboardMarkup
import telegramium.bots.Message
import telegramium.bots.high.Api
import telegramium.bots.high.Methods
import telegramium.bots.high.implicits._
import tofu.logging.Logging
import tofu.logging.Logs

import ru.johnspade.s10ns.CbDataUserRoutes
import ru.johnspade.s10ns.bot.CallbackQueryUserController
import ru.johnspade.s10ns.bot.DefCurrency
import ru.johnspade.s10ns.bot.Errors
import ru.johnspade.s10ns.bot.NotifyByDefault
import ru.johnspade.s10ns.bot.SettingsDialog
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.engine.TelegramOps.ackCb
import ru.johnspade.s10ns.bot.engine.TelegramOps.sendReplyMessages
import ru.johnspade.s10ns.bot.engine.TelegramOps.toReplyMessage
import ru.johnspade.s10ns.user.User

class SettingsController[F[_]: Logging: Temporal: Defer](
    private val settingsService: SettingsService[F]
)(implicit bot: Api[F])
    extends CallbackQueryUserController[F] {
  def message(user: User, dialog: SettingsDialog, message: Message): F[List[ReplyMessage]] =
    (dialog.state match {
      case SettingsDialogState.DefaultCurrency =>
        settingsService.saveDefaultCurrency(user, message.text).map(toReplyMessage).some
      case _ => Option.empty[F[ReplyMessage]]
    }).getOrElse(Monad[F].pure(ReplyMessage(Errors.Default)))
      .map(List(_))

  override val routes: CbDataUserRoutes[F] = CallbackQueryContextRoutes.of {
    case DefCurrency in cb as user =>
      ackCb(cb) *> cb.message
        .map { msg =>
          settingsService
            .startDefaultCurrencyDialog(user)
            .flatMap(sendReplyMessages(msg, _).void)
        }
        .getOrElse(Monad[F].unit)

    case NotifyByDefault in cb as user =>
      for {
        _     <- ackCb(cb)
        reply <- settingsService.toggleNotifyByDefault(user)
        _ <- Methods
          .editMessageText(
            chatId = cb.message.map(msg => ChatIntId(msg.chat.id)),
            messageId = cb.message.map(_.messageId),
            text = reply.text,
            replyMarkup = reply.markup match {
              case Some(m: InlineKeyboardMarkup) => m.some
              case _                             => None
            },
            parseMode = reply.parseMode
          )
          .exec
          .void
      } yield ()
  }

  def settingsCommand(user: User): F[ReplyMessage] = settingsService.onSettingsCommand(user)
}

object SettingsController {
  def apply[F[_]: Temporal: Defer](
      settingsService: SettingsService[F]
  )(implicit bot: Api[F], logs: Logs[F, F]): F[SettingsController[F]] =
    logs.forService[SettingsController[F]].map { implicit l =>
      new SettingsController[F](settingsService)
    }
}
