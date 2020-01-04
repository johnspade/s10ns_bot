package ru.johnspade.s10ns.subscription.controller

import cats.Monad.ops._
import cats.effect.{Sync, Timer}
import cats.syntax.option._
import io.chrisdavenport.log4cats.Logger
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.engine.TelegramOps.{ackCb, clearMarkup, handleCallback, sendReplyMessage, singleTextMessage, toReplyMessages}
import ru.johnspade.s10ns.bot.{EditS10nAmount, EditS10nAmountDialog, EditS10nBillingPeriod, EditS10nBillingPeriodDialog, EditS10nFirstPaymentDate, EditS10nFirstPaymentDateDialog, EditS10nName, EditS10nNameDialog, EditS10nOneTime, EditS10nOneTimeDialog, Errors, FirstPayment, OneTime, PeriodUnit}
import ru.johnspade.s10ns.subscription.dialog.{EditS10nAmountDialogState, EditS10nBillingPeriodDialogState, EditS10nNameDialogState, EditS10nOneTimeDialogState}
import ru.johnspade.s10ns.subscription.service.EditS10nDialogService
import ru.johnspade.s10ns.user.User
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

  def removeS10nIsOneTimeCb(cb: CallbackQuery, user: User, dialog: EditS10nOneTimeDialog)(implicit bot: Api[F]): F[Unit] =
    clearMarkup(cb) *> editS10nDialogService.removeIsOneTime(cb, user, dialog).flatMap(handleCallback(cb, _))

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

  def editS10nBillingPeriodCb(user: User, cb: CallbackQuery, data: EditS10nBillingPeriod)(implicit bot: Api[F]): F[Unit] =
    editS10nDialogService.onEditS10nBillingPeriodCb(user, cb, data).flatMap(reply(cb, _))

  def s10nBillingPeriodCb(cb: CallbackQuery, data: PeriodUnit, user: User, dialog: EditS10nBillingPeriodDialog)
    (implicit bot: Api[F]): F[Unit] =
    clearMarkup(cb) *> editS10nDialogService.saveBillingPeriodUnit(cb, data, user, dialog).flatMap(handleCallback(cb, _))

  def s10nBillingPeriodDurationMessage(user: User, dialog: EditS10nBillingPeriodDialog, message: Message): F[List[ReplyMessage]] =
    dialog.state match {
      case EditS10nBillingPeriodDialogState.BillingPeriodDuration =>
        editS10nDialogService.saveBillingPeriodDuration(user, dialog, message.text).map(toReplyMessages)
      case _ => defaultError
    }

  def editS10nFirstPaymentDateCb(user: User, cb: CallbackQuery, data: EditS10nFirstPaymentDate)(implicit bot: Api[F]): F[Unit] =
    editS10nDialogService.onEditS10nFirstPaymentDateCb(user, cb, data).flatMap(reply(cb, _))

  def s10nFirstPaymentDateCb(cb: CallbackQuery, data: FirstPayment, user: User, dialog: EditS10nFirstPaymentDateDialog)
    (implicit bot: Api[F]): F[Unit] =
    clearMarkup(cb) *> editS10nDialogService.saveFirstPaymentDate(cb, data, user, dialog).flatMap(handleCallback(cb, _))

  private def reply(cb: CallbackQuery, message: ReplyMessage)(implicit bot: Api[F]) =
    cb.message.map {
      ackCb(cb) *> sendReplyMessage(_, message)
    } getOrElse ackCb(cb, Errors.default.some)

  private val defaultError = Sync[F].pure(singleTextMessage(Errors.default))
}
