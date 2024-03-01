package ru.johnspade.s10ns.subscription.controller

import cats.Defer
import cats.Monad
import cats.effect.Temporal
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.option._

import ru.johnspade.tgbot.callbackqueries.CallbackQueryContextRoutes
import telegramium.bots.CallbackQuery
import telegramium.bots.ChatIntId
import telegramium.bots.Html
import telegramium.bots.Message
import telegramium.bots.high.Api
import telegramium.bots.high.Methods
import telegramium.bots.high.implicits._
import tofu.logging.Logging
import tofu.logging.Logs

import ru.johnspade.s10ns.CbDataUserRoutes
import ru.johnspade.s10ns.ackDefaultError
import ru.johnspade.s10ns.bot.CallbackQueryUserController
import ru.johnspade.s10ns.bot.CbData
import ru.johnspade.s10ns.bot.CreateS10nDialog
import ru.johnspade.s10ns.bot.DropFirstPayment
import ru.johnspade.s10ns.bot.EditS10nAmount
import ru.johnspade.s10ns.bot.EditS10nAmountDialog
import ru.johnspade.s10ns.bot.EditS10nBillingPeriod
import ru.johnspade.s10ns.bot.EditS10nBillingPeriodDialog
import ru.johnspade.s10ns.bot.EditS10nCurrency
import ru.johnspade.s10ns.bot.EditS10nCurrencyDialog
import ru.johnspade.s10ns.bot.EditS10nFirstPaymentDate
import ru.johnspade.s10ns.bot.EditS10nFirstPaymentDateDialog
import ru.johnspade.s10ns.bot.EditS10nName
import ru.johnspade.s10ns.bot.EditS10nNameDialog
import ru.johnspade.s10ns.bot.EditS10nOneTime
import ru.johnspade.s10ns.bot.EditS10nOneTimeDialog
import ru.johnspade.s10ns.bot.Errors
import ru.johnspade.s10ns.bot.EveryMonth
import ru.johnspade.s10ns.bot.FirstPayment
import ru.johnspade.s10ns.bot.OneTime
import ru.johnspade.s10ns.bot.PeriodUnit
import ru.johnspade.s10ns.bot.SkipIsOneTime
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.engine.TelegramOps.ackCb
import ru.johnspade.s10ns.bot.engine.TelegramOps.clearMarkup
import ru.johnspade.s10ns.bot.engine.TelegramOps.handleCallback
import ru.johnspade.s10ns.bot.engine.TelegramOps.sendReplyMessages
import ru.johnspade.s10ns.bot.engine.TelegramOps.singleTextMessage
import ru.johnspade.s10ns.bot.engine.TelegramOps.toReplyMessages
import ru.johnspade.s10ns.subscription.dialog.EditS10nAmountDialogState
import ru.johnspade.s10ns.subscription.dialog.EditS10nBillingPeriodDialogState
import ru.johnspade.s10ns.subscription.dialog.EditS10nCurrencyDialogState
import ru.johnspade.s10ns.subscription.dialog.EditS10nNameDialogState
import ru.johnspade.s10ns.subscription.dialog.EditS10nOneTimeDialogState
import ru.johnspade.s10ns.subscription.service.CreateS10nDialogService
import ru.johnspade.s10ns.subscription.service.EditS10n1stPaymentDateDialogService
import ru.johnspade.s10ns.subscription.service.EditS10nAmountDialogService
import ru.johnspade.s10ns.subscription.service.EditS10nBillingPeriodDialogService
import ru.johnspade.s10ns.subscription.service.EditS10nCurrencyDialogService
import ru.johnspade.s10ns.subscription.service.EditS10nNameDialogService
import ru.johnspade.s10ns.subscription.service.EditS10nOneTimeDialogService
import ru.johnspade.s10ns.user.User

class S10nController[F[_]: Logging: Temporal: Defer](
  private val createS10nDialogService: CreateS10nDialogService[F],
  private val editS10n1stPaymentDateDialogService: EditS10n1stPaymentDateDialogService[F],
  private val editS10nNameDialogService: EditS10nNameDialogService[F],
  private val editS10nAmountDialogService: EditS10nAmountDialogService[F],
  private val editS10nBillingPeriodDialogService: EditS10nBillingPeriodDialogService[F],
  private val editS10nCurrencyDialogService: EditS10nCurrencyDialogService[F],
  private val editS10nOneTimeDialogService: EditS10nOneTimeDialogService[F]
)(implicit bot: Api[F]) extends CallbackQueryUserController[F] {

  override val routes: CbDataUserRoutes[F] = CallbackQueryContextRoutes.of {
    case (data: PeriodUnit) in cb as user =>
      user.dialog.collect {
        case d: CreateS10nDialog =>
          clearMarkupAndSave(cb)(_.onBillingPeriodUnitCb(data, user, d)) *> showSelected(cb, data)
        case d: EditS10nOneTimeDialog =>
          clearMarkup(cb) *> editS10nOneTimeDialogService.saveBillingPeriodUnit(data, user, d)
            .flatMap(handleCallback(cb, _))
        case d: EditS10nBillingPeriodDialog =>
          clearMarkup(cb) *> editS10nBillingPeriodDialogService.saveBillingPeriodUnit(data, user, d)
            .flatMap(handleCallback(cb, _))
      }
        .getOrElse(ackDefaultError(cb))

    case SkipIsOneTime in cb as user =>
      user.dialog.collect {
        case d: CreateS10nDialog =>
          clearMarkupAndSave(cb)(_.onSkipIsOneTimeCb(user, d)) *> showSelected(cb, SkipIsOneTime)
        case d: EditS10nOneTimeDialog =>
          clearMarkup(cb) *> editS10nOneTimeDialogService.removeIsOneTime(user, d).flatMap(handleCallback(cb, _))
      }
        .getOrElse(ackDefaultError(cb))

    case (data: OneTime) in cb as user =>
      user.dialog.collect {
        case d: CreateS10nDialog =>
          clearMarkupAndSave(cb)(_.onIsOneTimeCallback(data, user, d)) *> showSelected(cb, data)
        case d: EditS10nOneTimeDialog =>
          clearMarkup(cb) *> editS10nOneTimeDialogService.saveIsOneTime(data, user, d).flatMap(handleCallback(cb, _))
      }
        .getOrElse(ackDefaultError(cb))

    case EveryMonth in cb as user =>
      user.dialog.collect {
        case d: CreateS10nDialog =>
          clearMarkupAndSave(cb)(_.onEveryMonthCb(user, d)) *> showSelected(cb, EveryMonth)
        case d: EditS10nOneTimeDialog =>
          clearMarkup(cb) *> editS10nOneTimeDialogService.saveEveryMonth(user, d).flatMap(handleCallback(cb, _))
      }
        .getOrElse(ackDefaultError(cb))

    case DropFirstPayment in cb as user =>
      user.dialog.collect {
        case d: CreateS10nDialog =>
          clearMarkupAndSave(cb)(_.onSkipFirstPaymentDateCb(user, d)) *> showSelected(cb, DropFirstPayment)
        case d: EditS10nFirstPaymentDateDialog =>
          clearMarkup(cb) *> editS10n1stPaymentDateDialogService.removeFirstPaymentDate(user, d)
            .flatMap(handleCallback(cb, _))
      }
        .getOrElse(ackDefaultError(cb))

    case (data: FirstPayment) in cb as user =>
      user.dialog.collect {
        case d: CreateS10nDialog =>
          clearMarkupAndSave(cb)(_.onFirstPaymentDateCb(data, user, d)) *> showSelected(cb, data)
        case d: EditS10nFirstPaymentDateDialog =>
          clearMarkup(cb) *> editS10n1stPaymentDateDialogService.saveFirstPaymentDate(data, user, d)
            .flatMap(handleCallback(cb, _))
      }
        .getOrElse(ackDefaultError(cb))

    case (data: EditS10nName) in cb as user =>
      editS10nNameDialogService.onEditS10nNameCb(user, data).flatMap(reply(cb, _))

    case (data: EditS10nAmount) in cb as user =>
      editS10nAmountDialogService.onEditS10nAmountCb(user, data).flatMap(reply(cb, _))

    case (data: EditS10nCurrency) in cb as user =>
      editS10nCurrencyDialogService.onEditS10nCurrencyCb(user, data).flatMap(reply(cb, _))

    case (data: EditS10nOneTime) in cb as user =>
      editS10nOneTimeDialogService.onEditS10nOneTimeCb(user, data).flatMap(reply(cb, _))

    case (data: EditS10nBillingPeriod) in cb as user =>
      editS10nBillingPeriodDialogService.onEditS10nBillingPeriodCb(user, data).flatMap(reply(cb, _))

    case (data: EditS10nFirstPaymentDate) in cb as user =>
      editS10n1stPaymentDateDialogService.onEditS10nFirstPaymentDateCb(user, data).flatMap(reply(cb, _))
  }

  def createCommand(user: User): F[List[ReplyMessage]] = createS10nDialogService.onCreateCommand(user)

  def createWithDefaultCurrencyCommand(user: User): F[List[ReplyMessage]] =
    createS10nDialogService.onCreateWithDefaultCurrencyCommand(user)

  def message(user: User, dialog: CreateS10nDialog, message: Message): F[List[ReplyMessage]] =
    createS10nDialogService.saveDraft
      .lift
      .apply(user, dialog, message.text)
      .map(_.map(toReplyMessages))
      .getOrElse(Monad[F].pure(singleTextMessage(Errors.UseInlineKeyboard)))

  def s10nNameMessage(user: User, dialog: EditS10nNameDialog, message: Message): F[List[ReplyMessage]] =
    dialog.state match {
      case EditS10nNameDialogState.Name =>
        editS10nNameDialogService.saveName(user, dialog, message.text).map(toReplyMessages)
      case _ => useInlineKeyboardError
    }

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

  def s10nBillingPeriodDurationMessage(user: User, dialog: EditS10nOneTimeDialog, message: Message): F[List[ReplyMessage]] =
    dialog.state match {
      case EditS10nOneTimeDialogState.BillingPeriodDuration =>
        editS10nOneTimeDialogService.saveBillingPeriodDuration(user, dialog, message.text).map(toReplyMessages)
      case _ => useInlineKeyboardError
    }

  def s10nBillingPeriodDurationMessage(user: User, dialog: EditS10nBillingPeriodDialog, message: Message): F[List[ReplyMessage]] =
    dialog.state match {
      case EditS10nBillingPeriodDialogState.BillingPeriodDuration =>
        editS10nBillingPeriodDialogService.saveBillingPeriodDuration(user, dialog, message.text).map(toReplyMessages)
      case _ => useInlineKeyboardError
    }

  private def reply(cb: CallbackQuery, messages: List[ReplyMessage])(implicit bot: Api[F]): F[Unit] = {
    cb.message.map {
      ackCb(cb) *> sendReplyMessages(_, messages)
    } getOrElse ackCb(cb, Errors.Default.some)
  }

  private def clearMarkupAndSave(cb: CallbackQuery)(f: CreateS10nDialogService[F] => F[List[ReplyMessage]]): F[Unit] =
    clearMarkup(cb) *> f(createS10nDialogService).flatMap(handleCallback(cb, _))

  private def showSelected(cb: CallbackQuery, data: CbData): F[Unit] =
    Methods.editMessageText(
      cb.message.map(msg => ChatIntId(msg.chat.id)),
      cb.message.map(_.messageId),
      text = cb.message.flatMap(_.text).map(t => s"$t <em>${data.print}</em>").orEmpty,
      parseMode = Html.some
    ).exec.void

  private val useInlineKeyboardError = Monad[F].pure(singleTextMessage(Errors.UseInlineKeyboard))
}

object S10nController {
  def apply[F[_]: Temporal: Defer](
    createS10nDialogService: CreateS10nDialogService[F],
    editS10n1stPaymentDateDialogService: EditS10n1stPaymentDateDialogService[F],
    editS10nNameDialogService: EditS10nNameDialogService[F],
    editS10nAmountDialogService: EditS10nAmountDialogService[F],
    editS10nBillingPeriodDialogService: EditS10nBillingPeriodDialogService[F],
    editS10nCurrencyDialogService: EditS10nCurrencyDialogService[F],
    editS10nOneTimeDialogService: EditS10nOneTimeDialogService[F]
  )(implicit bot: Api[F], logs: Logs[F, F]): F[S10nController[F]] =
    logs.forService[S10nController[F]].map { implicit l =>
      new S10nController[F](
        createS10nDialogService,
        editS10n1stPaymentDateDialogService,
        editS10nNameDialogService,
        editS10nAmountDialogService,
        editS10nBillingPeriodDialogService,
        editS10nCurrencyDialogService,
        editS10nOneTimeDialogService
      )
    }
}
