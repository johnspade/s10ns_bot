package ru.johnspade.s10ns.subscription.service

import ru.johnspade.s10ns.bot.EditS10nBillingPeriod
import ru.johnspade.s10ns.bot.EditS10nBillingPeriodDialog
import ru.johnspade.s10ns.bot.PeriodUnit
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.user.User

trait EditS10nBillingPeriodDialogService[F[_]] {

  def onEditS10nBillingPeriodCb(user: User, data: EditS10nBillingPeriod): F[List[ReplyMessage]]

  def saveBillingPeriodUnit(data: PeriodUnit, user: User, dialog: EditS10nBillingPeriodDialog): F[List[ReplyMessage]]

  def saveBillingPeriodDuration(user: User, dialog: EditS10nBillingPeriodDialog, text: Option[String]): F[RepliesValidated]
}
