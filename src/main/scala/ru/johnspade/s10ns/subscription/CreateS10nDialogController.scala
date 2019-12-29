package ru.johnspade.s10ns.subscription

import cats.effect.{Sync, Timer}
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import ru.johnspade.s10ns.common.Errors
import ru.johnspade.s10ns.telegram.TelegramOps.{clearMarkup, handleCallback, singleTextMessage, toReplyMessages}
import ru.johnspade.s10ns.telegram.{FirstPayment, OneTime, PeriodUnit, ReplyMessage}
import ru.johnspade.s10ns.user.{CreateS10nDialog, User}
import telegramium.bots.client.Api
import telegramium.bots.{CallbackQuery, Message}

class CreateS10nDialogController[F[_] : Sync : Logger : Timer](
  private val createS10nDialogService: CreateS10nDialogService[F]
) {
  def createCommand(user: User): F[ReplyMessage] = createS10nDialogService.onCreateCommand(user)

  def createWithDefaultCurrencyCommand(user: User): F[ReplyMessage] =
    createS10nDialogService.onCreateWithDefaultCurrencyCommand(user)

  def message(user: User, dialog: CreateS10nDialog, message: Message): F[List[ReplyMessage]] =
    createS10nDialogService.saveDraft
      .lift
      .apply(user, dialog, message.text)
      .map(_.map(toReplyMessages))
      .getOrElse(Sync[F].pure(singleTextMessage(Errors.default)))

  def billingPeriodUnitCb(cb: CallbackQuery, data: PeriodUnit, user: User, dialog: CreateS10nDialog)(
    implicit bot: Api[F]
  ): F[Unit] =
    clearMarkup(cb) *> createS10nDialogService.onBillingPeriodUnitCb(cb, data, user, dialog).flatMap(handleCallback(cb, _))

  def isOneTimeCb(cb: CallbackQuery, data: OneTime, user: User, dialog: CreateS10nDialog)(implicit bot: Api[F]): F[Unit] =
    clearMarkup(cb) *> createS10nDialogService.onIsOneTimeCallback(cb, data, user, dialog).flatMap(handleCallback(cb, _))

  def firstPaymentDateCb(cb: CallbackQuery, data: FirstPayment, user: User, dialog: CreateS10nDialog)(
    implicit bot: Api[F]
  ): F[Unit] =
    clearMarkup(cb) *> createS10nDialogService.onFirstPaymentDateCallback(cb, data, user, dialog).flatMap(handleCallback(cb, _))
}
