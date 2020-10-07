package ru.johnspade.s10ns.subscription.service

import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.{EditS10nOneTime, EditS10nOneTimeDialog, OneTime, PeriodUnit}
import ru.johnspade.s10ns.user.User

trait EditS10nOneTimeDialogService[F[_]] {
  def saveEveryMonth(user: User, dialog: EditS10nOneTimeDialog): F[List[ReplyMessage]]

  def removeIsOneTime(user: User, dialog: EditS10nOneTimeDialog): F[List[ReplyMessage]]

  def saveIsOneTime(data: OneTime, user: User, dialog: EditS10nOneTimeDialog): F[List[ReplyMessage]]

  def onEditS10nOneTimeCb(user: User, data: EditS10nOneTime): F[List[ReplyMessage]]

  def saveBillingPeriodUnit(data: PeriodUnit, user: User, dialog: EditS10nOneTimeDialog): F[List[ReplyMessage]]

  def saveBillingPeriodDuration(user: User, dialog: EditS10nOneTimeDialog, text: Option[String]): F[RepliesValidated]
}
