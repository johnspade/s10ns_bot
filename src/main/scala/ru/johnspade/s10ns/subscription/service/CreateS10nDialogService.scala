package ru.johnspade.s10ns.subscription.service

import ru.johnspade.s10ns.bot.{CreateS10nDialog, FirstPayment, OneTime, PeriodUnit, ValidatorNec}
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.user.User
import telegramium.bots.CallbackQuery

trait CreateS10nDialogService[F[_]] {
  def onCreateCommand(user: User): F[ReplyMessage]

  def onCreateWithDefaultCurrencyCommand(user: User): F[ReplyMessage]

  val saveDraft: PartialFunction[(User, CreateS10nDialog, Option[String]), F[ValidatorNec.ValidationResult[List[ReplyMessage]]]]

  def onBillingPeriodUnitCb(cb: CallbackQuery, data: PeriodUnit, user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]]

  def onSkipIsOneTimeCb(cb: CallbackQuery, user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]]

  def onIsOneTimeCallback(cb: CallbackQuery, data: OneTime, user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]]

  def onSkipFirstPaymentDateCb(cb: CallbackQuery, user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]]

  def onFirstPaymentDateCallback(cb: CallbackQuery, data: FirstPayment, user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]]
}
