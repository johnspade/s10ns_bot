package ru.johnspade.s10ns.subscription.service
import org.joda.money.CurrencyUnit
import ru.johnspade.s10ns.bot.CreateS10nDialog
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.subscription.tags.{BillingPeriodDuration, BillingPeriodUnit, FirstPaymentDate, OneTimeSubscription, SubscriptionName}
import ru.johnspade.s10ns.user.User

trait CreateS10nDialogFsmService[F[_]] {
  def saveName(user: User, dialog: CreateS10nDialog, name: SubscriptionName): F[List[ReplyMessage]]

  def saveCurrency(user: User, dialog: CreateS10nDialog, currency: CurrencyUnit): F[List[ReplyMessage]]

  def saveAmount(user: User, dialog: CreateS10nDialog, amount: BigDecimal): F[List[ReplyMessage]]

  def saveBillingPeriodDuration(user: User, dialog: CreateS10nDialog, duration: BillingPeriodDuration): F[List[ReplyMessage]]

  def saveBillingPeriodUnit(user: User, dialog: CreateS10nDialog, unit: BillingPeriodUnit): F[List[ReplyMessage]]

  def skipIsOneTime(user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]]

  def saveIsOneTime(user: User, dialog: CreateS10nDialog, oneTime: OneTimeSubscription): F[List[ReplyMessage]]

  def skipFirstPaymentDate(user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]]

  def saveFirstPaymentDate(user: User, dialog: CreateS10nDialog, date: FirstPaymentDate): F[List[ReplyMessage]]
}
