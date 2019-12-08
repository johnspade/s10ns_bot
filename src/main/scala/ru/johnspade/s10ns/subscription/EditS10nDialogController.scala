package ru.johnspade.s10ns.subscription

import cats.effect.Sync
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import ru.johnspade.s10ns.common.Errors
import ru.johnspade.s10ns.telegram.{EditS10nName, ReplyMessage}
import ru.johnspade.s10ns.telegram.TelegramOps.{ackCb, sendReplyMessage, toReplyMessage}
import ru.johnspade.s10ns.user.{EditS10nNameDialog, EditS10nNameDialogState, User}
import telegramium.bots.client.Api
import telegramium.bots.{CallbackQuery, Message}

class EditS10nDialogController[F[_] : Sync : Logger](
  private val editS10nDialogService: EditS10nDialogService[F]
) {
  def editS10nNameCb(cb: CallbackQuery, data: EditS10nName)(implicit bot: Api[F]): F[Unit] =
    editS10nDialogService.onEditS10nNameCb(cb, data)
      .flatMap { reply =>
        cb.message.map {
          ackCb(cb) *> sendReplyMessage(_, reply)
        } getOrElse ackCb(cb, Errors.default.some)
      }

  def s10nNameMessage(user: User, dialog: EditS10nNameDialog, message: Message): F[ReplyMessage] =
    dialog.state match {
      case EditS10nNameDialogState.Name => editS10nDialogService.saveName(user, dialog, message.text).map(toReplyMessage)
      case _ => Sync[F].pure(ReplyMessage(Errors.default))
    }
}
