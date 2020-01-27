package ru.johnspade.s10ns.subscription.service
import ru.johnspade.s10ns.bot.ValidatorNec.ValidationResult
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.{EditS10nAmount, EditS10nAmountDialog, EditS10nBillingPeriod, EditS10nBillingPeriodDialog, EditS10nCurrency, EditS10nCurrencyDialog, EditS10nFirstPaymentDate, EditS10nFirstPaymentDateDialog, EditS10nName, EditS10nNameDialog, EditS10nOneTime, EditS10nOneTimeDialog, FirstPayment, OneTime, PeriodUnit}
import ru.johnspade.s10ns.user.User
import telegramium.bots.CallbackQuery

trait EditS10nDialogService[F[_]] {
  type RepliesValidated = ValidationResult[List[ReplyMessage]]

  def onEditS10nNameCb(user: User, cb: CallbackQuery, data: EditS10nName): F[ReplyMessage]

  def saveName(user: User, dialog: EditS10nNameDialog, text: Option[String]): F[RepliesValidated]

  def onEditS10nAmountCb(user: User, cb: CallbackQuery, data: EditS10nAmount): F[ReplyMessage]

  def onEditS10nCurrencyCb(user: User, cb: CallbackQuery, data: EditS10nCurrency): F[ReplyMessage]

  def saveAmount(user: User, dialog: EditS10nAmountDialog, text: Option[String]): F[RepliesValidated]

  def saveCurrency(user: User, dialog: EditS10nCurrencyDialog, text: Option[String]): F[RepliesValidated]

  def saveAmount(user: User, dialog: EditS10nCurrencyDialog, text: Option[String]): F[RepliesValidated]

  def removeIsOneTime(cb: CallbackQuery, user: User, dialog: EditS10nOneTimeDialog): F[List[ReplyMessage]]

  def saveIsOneTime(cb: CallbackQuery, data: OneTime, user: User, dialog: EditS10nOneTimeDialog): F[List[ReplyMessage]]

  def onEditS10nOneTimeCb(user: User, cb: CallbackQuery, data: EditS10nOneTime): F[ReplyMessage]

  def saveBillingPeriodUnit(cb: CallbackQuery, data: PeriodUnit, user: User, dialog: EditS10nOneTimeDialog): F[List[ReplyMessage]]

  def saveBillingPeriodDuration(user: User, dialog: EditS10nOneTimeDialog, text: Option[String]): F[RepliesValidated]

  def onEditS10nBillingPeriodCb(user: User, cb: CallbackQuery, data: EditS10nBillingPeriod): F[ReplyMessage]

  def saveBillingPeriodUnit(cb: CallbackQuery, data: PeriodUnit, user: User, dialog: EditS10nBillingPeriodDialog): F[List[ReplyMessage]]

  def saveBillingPeriodDuration(user: User, dialog: EditS10nBillingPeriodDialog, text: Option[String]): F[RepliesValidated]

  def onEditS10nFirstPaymentDateCb(user: User, cb: CallbackQuery, data: EditS10nFirstPaymentDate): F[ReplyMessage]

  def removeFirstPaymentDate(user: User, dialog: EditS10nFirstPaymentDateDialog): F[List[ReplyMessage]]

  def saveFirstPaymentDate(
    cb: CallbackQuery,
    data: FirstPayment,
    user: User,
    dialog: EditS10nFirstPaymentDateDialog
  ): F[List[ReplyMessage]]
}
