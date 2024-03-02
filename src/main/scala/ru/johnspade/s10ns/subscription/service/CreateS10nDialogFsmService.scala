package ru.johnspade.s10ns.subscription.service

import java.time.LocalDate

import org.joda.money.CurrencyUnit

import ru.johnspade.s10ns.bot.CreateS10nDialog
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.subscription.BillingPeriodUnit
import ru.johnspade.s10ns.user.User

trait CreateS10nDialogFsmService[F[_]] {
  def saveName(user: User, dialog: CreateS10nDialog, name: String): F[List[ReplyMessage]]

  def saveCurrency(user: User, dialog: CreateS10nDialog, currency: CurrencyUnit): F[List[ReplyMessage]]

  def saveAmount(user: User, dialog: CreateS10nDialog, amount: BigDecimal): F[List[ReplyMessage]]

  def saveBillingPeriodDuration(
      user: User,
      dialog: CreateS10nDialog,
      duration: Int
  ): F[List[ReplyMessage]]

  def saveBillingPeriodUnit(user: User, dialog: CreateS10nDialog, unit: BillingPeriodUnit): F[List[ReplyMessage]]

  def saveEveryMonth(user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]]

  def skipIsOneTime(user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]]

  def saveIsOneTime(user: User, dialog: CreateS10nDialog, oneTime: Boolean): F[List[ReplyMessage]]

  def skipFirstPaymentDate(user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]]

  def saveFirstPaymentDate(user: User, dialog: CreateS10nDialog, date: LocalDate): F[List[ReplyMessage]]
}
