package ru.johnspade.s10ns.subscription.service

import ru.johnspade.s10ns.bot.CreateS10nDialog
import ru.johnspade.s10ns.bot.FirstPayment
import ru.johnspade.s10ns.bot.OneTime
import ru.johnspade.s10ns.bot.PeriodUnit
import ru.johnspade.s10ns.bot.ValidatorNec
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.user.User

trait CreateS10nDialogService[F[_]] {
  def onCreateCommand(user: User): F[List[ReplyMessage]]

  def onCreateWithDefaultCurrencyCommand(user: User): F[List[ReplyMessage]]

  val saveDraft: PartialFunction[(User, CreateS10nDialog, Option[String]), F[
    ValidatorNec.ValidationResult[List[ReplyMessage]]
  ]]

  def onBillingPeriodUnitCb(data: PeriodUnit, user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]]

  def onEveryMonthCb(user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]]

  def onSkipIsOneTimeCb(user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]]

  def onIsOneTimeCallback(data: OneTime, user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]]

  def onSkipFirstPaymentDateCb(user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]]

  def onFirstPaymentDateCb(data: FirstPayment, user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]]
}
