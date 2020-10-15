package ru.johnspade.s10ns.subscription.controller

import cats.effect.{Sync, Timer}
import cats.implicits._
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.engine.TelegramOps.{clearMarkup, handleCallback, singleTextMessage, toReplyMessages}
import ru.johnspade.s10ns.bot.{CbData, CreateS10nDialog, DropFirstPayment, Errors, EveryMonth, FirstPayment, OneTime, PeriodUnit, SkipIsOneTime}
import ru.johnspade.s10ns.subscription.service.CreateS10nDialogService
import ru.johnspade.s10ns.user.User
import telegramium.bots.high.implicits._
import telegramium.bots.high.{Api, Methods}
import telegramium.bots.{CallbackQuery, ChatIntId, Html, Message}
import tofu.logging.{Logging, Logs}

class CreateS10nDialogController[F[_]: Sync: Timer: Logging](
  private val createS10nDialogService: CreateS10nDialogService[F]
) {
  def createCommand(user: User): F[List[ReplyMessage]] = createS10nDialogService.onCreateCommand(user)

  def createWithDefaultCurrencyCommand(user: User): F[List[ReplyMessage]] =
    createS10nDialogService.onCreateWithDefaultCurrencyCommand(user)

  def message(user: User, dialog: CreateS10nDialog, message: Message): F[List[ReplyMessage]] =
    createS10nDialogService.saveDraft
      .lift
      .apply(user, dialog, message.text)
      .map(_.map(toReplyMessages))
      .getOrElse(Sync[F].pure(singleTextMessage(Errors.UseInlineKeyboard)))

  def billingPeriodUnitCb(cb: CallbackQuery, data: PeriodUnit, user: User, dialog: CreateS10nDialog)(
    implicit bot: Api[F]
  ): F[Unit] = clearMarkupAndSave(cb)(_.onBillingPeriodUnitCb(data, user, dialog)) *> showSelected(cb, data)

  def everyMonthCb(cb: CallbackQuery, user: User, dialog: CreateS10nDialog)(implicit bot: Api[F]): F[Unit] =
    clearMarkupAndSave(cb)(_.onEveryMonthCb(user, dialog)) *> showSelected(cb, EveryMonth)

  def skipIsOneTimeCb(cb: CallbackQuery, user: User, dialog: CreateS10nDialog)(implicit bot: Api[F]): F[Unit] =
    clearMarkupAndSave(cb)(_.onSkipIsOneTimeCb(user, dialog)) *> showSelected(cb, SkipIsOneTime)

  def isOneTimeCb(cb: CallbackQuery, data: OneTime, user: User, dialog: CreateS10nDialog)(implicit bot: Api[F]): F[Unit] =
    clearMarkupAndSave(cb)(_.onIsOneTimeCallback(data, user, dialog)) *> showSelected(cb, data)

  def skipFirstPaymentDateCb(cb: CallbackQuery, user: User, dialog: CreateS10nDialog)(implicit bot: Api[F]): F[Unit] =
    clearMarkupAndSave(cb)(_.onSkipFirstPaymentDateCb(user, dialog)) *> showSelected(cb, DropFirstPayment)

  def firstPaymentDateCb(cb: CallbackQuery, data: FirstPayment, user: User, dialog: CreateS10nDialog)(
    implicit bot: Api[F]
  ): F[Unit] = clearMarkupAndSave(cb)(_.onFirstPaymentDateCb(data, user, dialog)) *> showSelected(cb, data)

  private def clearMarkupAndSave(cb: CallbackQuery)(f: CreateS10nDialogService[F] => F[List[ReplyMessage]])(
    implicit bot: Api[F]
  ): F[Unit] = clearMarkup(cb) *> f(createS10nDialogService).flatMap(handleCallback(cb, _))

  private def showSelected(cb: CallbackQuery, data: CbData)(implicit bot: Api[F]): F[Unit] =
    Methods.editMessageText(
      cb.message.map(msg => ChatIntId(msg.chat.id)),
      cb.message.map(_.messageId),
      text = cb.message.flatMap(_.text).map(t => s"$t <em>${data.print}</em>").orEmpty,
      parseMode = Html.some
    ).exec.void
}

object CreateS10nDialogController {
  def apply[F[_]: Sync: Timer](createS10nDialogService: CreateS10nDialogService[F])(
    implicit logs: Logs[F, F]
  ): F[CreateS10nDialogController[F]] =
    logs.forService[CreateS10nDialogController[F]].map { implicit l =>
      new CreateS10nDialogController[F](createS10nDialogService)
    }
}
