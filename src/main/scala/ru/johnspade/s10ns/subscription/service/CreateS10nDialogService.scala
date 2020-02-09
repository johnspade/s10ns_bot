package ru.johnspade.s10ns.subscription.service

import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.{CreateS10nDialog, FirstPayment, OneTime, PeriodUnit, ValidatorNec}
import ru.johnspade.s10ns.user.User

trait CreateS10nDialogService[F[_]] {
  def onCreateCommand(user: User): F[ReplyMessage]

  def onCreateWithDefaultCurrencyCommand(user: User): F[ReplyMessage]

  val saveDraft: PartialFunction[(User, CreateS10nDialog, Option[String]), F[ValidatorNec.ValidationResult[List[ReplyMessage]]]]

  def onBillingPeriodUnitCb(data: PeriodUnit, user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]]

  def onSkipIsOneTimeCb(user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]]

  def onIsOneTimeCallback(data: OneTime, user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]]

  def onSkipFirstPaymentDateCb(user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]]

  def onFirstPaymentDateCallback(data: FirstPayment, user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]]
}
