package ru.johnspade.s10ns.subscription

import cats.effect.Sync
import cats.implicits._
import ru.johnspade.s10ns.common.Errors
import ru.johnspade.s10ns.telegram.TelegramOps.{ackCb, sendReplyMessage, singleTextMessage, toReplyMessages}
import ru.johnspade.s10ns.telegram.{EditS10nAmount, EditS10nName, ReplyMessage}
import ru.johnspade.s10ns.user.{EditS10nAmountDialog, EditS10nAmountDialogState, EditS10nNameDialog, EditS10nNameDialogState, User}
import telegramium.bots.client.Api
import telegramium.bots.{CallbackQuery, Message}

class EditS10nDialogController[F[_] : Sync](
  private val editS10nDialogService: EditS10nDialogService[F]
) {
  def editS10nNameCb(cb: CallbackQuery, data: EditS10nName)(implicit bot: Api[F]): F[Unit] =
    editS10nDialogService.onEditS10nNameCb(cb, data).flatMap(reply(cb, _))

  def s10nNameMessage(user: User, dialog: EditS10nNameDialog, message: Message): F[List[ReplyMessage]] =
    dialog.state match {
      case EditS10nNameDialogState.Name =>
        editS10nDialogService.saveName(user, dialog, message.text).map(toReplyMessages)
      case _ => defaultError
    }

  def editS10nAmountCb(cb: CallbackQuery, data: EditS10nAmount)(implicit bot: Api[F]): F[Unit] =
    editS10nDialogService.onEditS10nAmountCb(cb, data).flatMap(reply(cb, _))

  def s10nAmountMessage(user: User, dialog: EditS10nAmountDialog, message: Message): F[List[ReplyMessage]] =
    dialog.state match {
      case EditS10nAmountDialogState.Currency =>
        editS10nDialogService.saveCurrency(user, dialog, message.text).map(toReplyMessages)
      case EditS10nAmountDialogState.Amount =>
        editS10nDialogService.saveAmount(user, dialog, message.text).map(toReplyMessages)
      case _ => defaultError
    }

  private def reply(cb: CallbackQuery, message: ReplyMessage)(implicit bot: Api[F]) =
    cb.message.map {
      ackCb(cb) *> sendReplyMessage(_, message)
    } getOrElse ackCb(cb, Errors.default.some)

  private val defaultError = Sync[F].pure(singleTextMessage(Errors.default))
}
