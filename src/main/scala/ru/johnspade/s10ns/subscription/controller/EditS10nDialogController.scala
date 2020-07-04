package ru.johnspade.s10ns.subscription.controller

import cats.Monad.ops._
import cats.effect.{Sync, Timer}
import cats.syntax.option._
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.engine.TelegramOps.{ackCb, clearMarkup, handleCallback, sendReplyMessages, singleTextMessage, toReplyMessages}
import ru.johnspade.s10ns.bot.{EditS10nAmount, EditS10nAmountDialog, EditS10nBillingPeriod, EditS10nBillingPeriodDialog, EditS10nCurrency, EditS10nCurrencyDialog, EditS10nFirstPaymentDate, EditS10nFirstPaymentDateDialog, EditS10nName, EditS10nNameDialog, EditS10nOneTime, EditS10nOneTimeDialog, Errors, FirstPayment, OneTime, PeriodUnit}
import ru.johnspade.s10ns.subscription.dialog.{EditS10nAmountDialogState, EditS10nBillingPeriodDialogState, EditS10nCurrencyDialogState, EditS10nNameDialogState, EditS10nOneTimeDialogState}
import ru.johnspade.s10ns.subscription.service.{EditS10n1stPaymentDateDialogService, EditS10nAmountDialogService, EditS10nBillingPeriodDialogService, EditS10nCurrencyDialogService, EditS10nNameDialogService, EditS10nOneTimeDialogService}
import ru.johnspade.s10ns.user.User
import telegramium.bots.high.Api
import telegramium.bots.{CallbackQuery, Message}
import tofu.logging.{Logging, Logs}

class EditS10nDialogController[F[_]: Sync: Logging: Timer](
  private val editS10n1stPaymentDateDialogService: EditS10n1stPaymentDateDialogService[F],
  private val editS10nNameDialogService: EditS10nNameDialogService[F],
  private val editS10nAmountDialogService: EditS10nAmountDialogService[F],
  private val editS10nBillingPeriodDialogService: EditS10nBillingPeriodDialogService[F],
  private val editS10nCurrencyDialogService: EditS10nCurrencyDialogService[F],
  private val editS10nOneTimeDialogService: EditS10nOneTimeDialogService[F]
) {
  def editS10nNameCb(user: User, cb: CallbackQuery, data: EditS10nName)(implicit bot: Api[F]): F[Unit] =
    editS10nNameDialogService.onEditS10nNameCb(user, cb, data).flatMap(reply(cb, _))

  def s10nNameMessage(user: User, dialog: EditS10nNameDialog, message: Message): F[List[ReplyMessage]] =
    dialog.state match {
      case EditS10nNameDialogState.Name =>
        editS10nNameDialogService.saveName(user, dialog, message.text).map(toReplyMessages)
      case _ => useInlineKeyboardError
    }

  def editS10nAmountCb(user: User, cb: CallbackQuery, data: EditS10nAmount)(implicit bot: Api[F]): F[Unit] =
    editS10nAmountDialogService.onEditS10nAmountCb(user, cb, data).flatMap(reply(cb, _))

  def editS10nCurrencyCb(user: User, cb: CallbackQuery, data: EditS10nCurrency)(implicit bot: Api[F]): F[Unit] =
    editS10nCurrencyDialogService.onEditS10nCurrencyCb(user, cb, data).flatMap(reply(cb, _))

  def s10nEditAmountMessage(user: User, dialog: EditS10nAmountDialog, message: Message): F[List[ReplyMessage]] =
    dialog.state match {
      case EditS10nAmountDialogState.Amount =>
        editS10nAmountDialogService.saveAmount(user, dialog, message.text).map(toReplyMessages)
      case _ => useInlineKeyboardError
    }

  def s10nEditCurrencyMessage(user: User, dialog: EditS10nCurrencyDialog, message: Message): F[List[ReplyMessage]] =
    dialog.state match {
      case EditS10nCurrencyDialogState.Currency =>
        editS10nCurrencyDialogService.saveCurrency(user, dialog, message.text).map(toReplyMessages)
      case EditS10nCurrencyDialogState.Amount =>
        editS10nCurrencyDialogService.saveAmount(user, dialog, message.text).map(toReplyMessages)
      case _ => useInlineKeyboardError
    }

  def editS10nOneTimeCb(user: User, cb: CallbackQuery, data: EditS10nOneTime)(implicit bot: Api[F]): F[Unit] =
    editS10nOneTimeDialogService.onEditS10nOneTimeCb(user, cb, data).flatMap(reply(cb, _))

  def everyMonthCb(cb: CallbackQuery, user: User, dialog: EditS10nOneTimeDialog)(implicit bot: Api[F]): F[Unit] =
    clearMarkup(cb) *> editS10nOneTimeDialogService.saveEveryMonth(cb, user, dialog).flatMap(handleCallback(cb, _))

  def removeS10nIsOneTimeCb(cb: CallbackQuery, user: User, dialog: EditS10nOneTimeDialog)(implicit bot: Api[F]): F[Unit] =
    clearMarkup(cb) *> editS10nOneTimeDialogService.removeIsOneTime(cb, user, dialog).flatMap(handleCallback(cb, _))

  def s10nOneTimeCb(cb: CallbackQuery, data: OneTime, user: User, dialog: EditS10nOneTimeDialog)(implicit bot: Api[F]): F[Unit] =
    clearMarkup(cb) *> editS10nOneTimeDialogService.saveIsOneTime(cb, data, user, dialog).flatMap(handleCallback(cb, _))

  def s10nBillingPeriodCb(cb: CallbackQuery, data: PeriodUnit, user: User, dialog: EditS10nOneTimeDialog)(
    implicit bot: Api[F]
  ): F[Unit] =
    clearMarkup(cb) *> editS10nOneTimeDialogService.saveBillingPeriodUnit(cb, data, user, dialog).flatMap(handleCallback(cb, _))

  def s10nBillingPeriodDurationMessage(user: User, dialog: EditS10nOneTimeDialog, message: Message): F[List[ReplyMessage]] =
    dialog.state match {
      case EditS10nOneTimeDialogState.BillingPeriodDuration =>
        editS10nOneTimeDialogService.saveBillingPeriodDuration(user, dialog, message.text).map(toReplyMessages)
      case _ => useInlineKeyboardError
    }

  def editS10nBillingPeriodCb(user: User, cb: CallbackQuery, data: EditS10nBillingPeriod)(implicit bot: Api[F]): F[Unit] =
    editS10nBillingPeriodDialogService.onEditS10nBillingPeriodCb(user, cb, data).flatMap(reply(cb, _))

  def s10nBillingPeriodCb(cb: CallbackQuery, data: PeriodUnit, user: User, dialog: EditS10nBillingPeriodDialog)
    (implicit bot: Api[F]): F[Unit] =
    clearMarkup(cb) *> editS10nBillingPeriodDialogService.saveBillingPeriodUnit(cb, data, user, dialog).flatMap(handleCallback(cb, _))

  def s10nBillingPeriodDurationMessage(user: User, dialog: EditS10nBillingPeriodDialog, message: Message): F[List[ReplyMessage]] =
    dialog.state match {
      case EditS10nBillingPeriodDialogState.BillingPeriodDuration =>
        editS10nBillingPeriodDialogService.saveBillingPeriodDuration(user, dialog, message.text).map(toReplyMessages)
      case _ => useInlineKeyboardError
    }

  def editS10nFirstPaymentDateCb(user: User, cb: CallbackQuery, data: EditS10nFirstPaymentDate)(implicit bot: Api[F]): F[Unit] =
    editS10n1stPaymentDateDialogService.onEditS10nFirstPaymentDateCb(user, cb, data).flatMap(reply(cb, _))

  def removeFirstPaymentDateCb(cb: CallbackQuery, user: User, dialog: EditS10nFirstPaymentDateDialog)
    (implicit bot: Api[F]): F[Unit] =
    clearMarkup(cb) *> editS10n1stPaymentDateDialogService.removeFirstPaymentDate(user, dialog).flatMap(handleCallback(cb, _))

  def s10nFirstPaymentDateCb(cb: CallbackQuery, data: FirstPayment, user: User, dialog: EditS10nFirstPaymentDateDialog)
    (implicit bot: Api[F]): F[Unit] =
    clearMarkup(cb) *> editS10n1stPaymentDateDialogService.saveFirstPaymentDate(cb, data, user, dialog)
      .flatMap(handleCallback(cb, _))

  private def reply(cb: CallbackQuery, messages: List[ReplyMessage])(implicit bot: Api[F]): F[Unit] = {
    cb.message.map {
      ackCb(cb) *> sendReplyMessages(_, messages)
    } getOrElse ackCb(cb, Errors.Default.some)
  }

  private val useInlineKeyboardError = Sync[F].pure(singleTextMessage(Errors.UseInlineKeyboard))
}

object EditS10nDialogController {
  def apply[F[_]: Sync: Timer](
    editS10n1stPaymentDateDialogService: EditS10n1stPaymentDateDialogService[F],
    editS10nNameDialogService: EditS10nNameDialogService[F],
    editS10nAmountDialogService: EditS10nAmountDialogService[F],
    editS10nBillingPeriodDialogService: EditS10nBillingPeriodDialogService[F],
    editS10nCurrencyDialogService: EditS10nCurrencyDialogService[F],
    editS10nOneTimeDialogService: EditS10nOneTimeDialogService[F]
  )(implicit logs: Logs[F, F]): F[EditS10nDialogController[F]] =
    logs.forService[EditS10nDialogController[F]].map { implicit l =>
      new EditS10nDialogController[F](
        editS10n1stPaymentDateDialogService,
        editS10nNameDialogService,
        editS10nAmountDialogService,
        editS10nBillingPeriodDialogService,
        editS10nCurrencyDialogService,
        editS10nOneTimeDialogService
      )
    }
}
