package ru.johnspade.s10ns.subscription

import cats.Monad.ops._
import cats.effect.{Sync, Timer}
import cats.syntax.option._
import io.chrisdavenport.log4cats.Logger
import ru.johnspade.s10ns.common.Errors
import ru.johnspade.s10ns.telegram.TelegramOps.{ackCb, clearMarkup, handleCallback, sendReplyMessage, singleTextMessage, toReplyMessages}
import ru.johnspade.s10ns.telegram.{EditS10nAmount, EditS10nName, EditS10nOneTime, OneTime, PeriodUnit, ReplyMessage}
import ru.johnspade.s10ns.user.{EditS10nAmountDialog, EditS10nAmountDialogState, EditS10nNameDialog, EditS10nNameDialogState, EditS10nOneTimeDialog, EditS10nOneTimeDialogState, User}
import telegramium.bots.client.Api
import telegramium.bots.{CallbackQuery, Message}

class EditS10nDialogController[F[_] : Sync : Logger : Timer](
  private val editS10nDialogService: EditS10nDialogService[F]
) {
  def editS10nNameCb(user: User, cb: CallbackQuery, data: EditS10nName)(implicit bot: Api[F]): F[Unit] =
    editS10nDialogService.onEditS10nNameCb(user, cb, data).flatMap(reply(cb, _))

  def s10nNameMessage(user: User, dialog: EditS10nNameDialog, message: Message): F[List[ReplyMessage]] =
    dialog.state match {
      case EditS10nNameDialogState.Name =>
        editS10nDialogService.saveName(user, dialog, message.text).map(toReplyMessages)
      case _ => defaultError
    }

  def editS10nAmountCb(user: User, cb: CallbackQuery, data: EditS10nAmount)(implicit bot: Api[F]): F[Unit] =
    editS10nDialogService.onEditS10nAmountCb(user, cb, data).flatMap(reply(cb, _))

  def s10nAmountMessage(user: User, dialog: EditS10nAmountDialog, message: Message): F[List[ReplyMessage]] =
    dialog.state match {
      case EditS10nAmountDialogState.Currency =>
        editS10nDialogService.saveCurrency(user, dialog, message.text).map(toReplyMessages)
      case EditS10nAmountDialogState.Amount =>
        editS10nDialogService.saveAmount(user, dialog, message.text).map(toReplyMessages)
      case _ => defaultError
    }

  def editS10nOneTimeCb(user: User, cb: CallbackQuery, data: EditS10nOneTime)(implicit bot: Api[F]): F[Unit] =
    editS10nDialogService.onEditS10nOneTimeCb(user, cb, data).flatMap(reply(cb, _))

  def s10nOneTimeCb(cb: CallbackQuery, data: OneTime, user: User, dialog: EditS10nOneTimeDialog)(implicit bot: Api[F]): F[Unit] =
    clearMarkup(cb) *> editS10nDialogService.saveIsOneTime(cb, data, user, dialog).flatMap(handleCallback(cb, _))

  def s10nBillingPeriodCb(cb: CallbackQuery, data: PeriodUnit, user: User, dialog: EditS10nOneTimeDialog)(
    implicit bot: Api[F]
  ): F[Unit] =
    clearMarkup(cb) *> editS10nDialogService.saveBillingPeriodUnit(cb, data, user, dialog).flatMap(handleCallback(cb, _))

  def s10nBillingPeriodDurationMessage(user: User, dialog: EditS10nOneTimeDialog, message: Message): F[List[ReplyMessage]] =
    dialog.state match {
      case EditS10nOneTimeDialogState.BillingPeriodDuration =>
        editS10nDialogService.saveBillingPeriodDuration(user, dialog, message.text).map(toReplyMessages)
      case _ => defaultError
    }

  private def reply(cb: CallbackQuery, message: ReplyMessage)(implicit bot: Api[F]) =
    cb.message.map {
      ackCb(cb) *> sendReplyMessage(_, message)
    } getOrElse ackCb(cb, Errors.default.some)

  private val defaultError = Sync[F].pure(singleTextMessage(Errors.default))
}
