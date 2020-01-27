package ru.johnspade.s10ns.subscription.service
import org.joda.money.CurrencyUnit
import ru.johnspade.s10ns.bot.{EditS10nAmountDialog, EditS10nBillingPeriodDialog, EditS10nCurrencyDialog, EditS10nFirstPaymentDateDialog, EditS10nNameDialog, EditS10nOneTimeDialog}
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.subscription.tags.{BillingPeriodDuration, BillingPeriodUnit, FirstPaymentDate, OneTimeSubscription, SubscriptionName}
import ru.johnspade.s10ns.user.User

trait EditS10nDialogFsmService[F[_]] {
  def saveName(user: User, dialog: EditS10nNameDialog, name: SubscriptionName): F[List[ReplyMessage]]

  def saveAmount(user: User, dialog: EditS10nAmountDialog, amount: BigDecimal): F[List[ReplyMessage]]

  def saveCurrency(user: User, dialog: EditS10nCurrencyDialog, currency: CurrencyUnit): F[List[ReplyMessage]]

  def saveAmount(user: User, dialog: EditS10nCurrencyDialog, amount: BigDecimal): F[List[ReplyMessage]]

  def removeIsOneTime(user: User, dialog: EditS10nOneTimeDialog): F[List[ReplyMessage]]

  def saveIsOneTime(user: User, dialog: EditS10nOneTimeDialog, oneTime: OneTimeSubscription): F[List[ReplyMessage]]

  def saveBillingPeriodUnit(user: User, dialog: EditS10nOneTimeDialog, unit: BillingPeriodUnit): F[List[ReplyMessage]]

  def saveBillingPeriodDuration(user: User, dialog: EditS10nOneTimeDialog, duration: BillingPeriodDuration): F[List[ReplyMessage]]

  def saveBillingPeriodUnit(user: User, dialog: EditS10nBillingPeriodDialog, unit: BillingPeriodUnit): F[List[ReplyMessage]]

  def saveBillingPeriodDuration(user: User, dialog: EditS10nBillingPeriodDialog, duration: BillingPeriodDuration): F[List[ReplyMessage]]

  def removeFirstPaymentDate(user: User, dialog: EditS10nFirstPaymentDateDialog): F[List[ReplyMessage]]

  def saveFirstPaymentDate(user: User, dialog: EditS10nFirstPaymentDateDialog, date: FirstPaymentDate): F[List[ReplyMessage]]
}
