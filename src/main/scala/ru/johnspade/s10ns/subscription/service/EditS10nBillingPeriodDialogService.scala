package ru.johnspade.s10ns.subscription.service

import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.{EditS10nBillingPeriod, EditS10nBillingPeriodDialog, PeriodUnit}
import ru.johnspade.s10ns.user.User
import telegramium.bots.CallbackQuery

trait EditS10nBillingPeriodDialogService[F[_]] {

  def onEditS10nBillingPeriodCb(user: User, cb: CallbackQuery, data: EditS10nBillingPeriod): F[List[ReplyMessage]]

  def saveBillingPeriodUnit(cb: CallbackQuery, data: PeriodUnit, user: User, dialog: EditS10nBillingPeriodDialog): F[List[ReplyMessage]]

  def saveBillingPeriodDuration(user: User, dialog: EditS10nBillingPeriodDialog, text: Option[String]): F[RepliesValidated]
}
