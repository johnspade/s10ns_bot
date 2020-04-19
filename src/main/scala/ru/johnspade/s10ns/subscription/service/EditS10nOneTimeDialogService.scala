package ru.johnspade.s10ns.subscription.service

import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.{EditS10nOneTime, EditS10nOneTimeDialog, OneTime, PeriodUnit}
import ru.johnspade.s10ns.user.User
import telegramium.bots.CallbackQuery

trait EditS10nOneTimeDialogService[F[_]] {
  def saveEveryMonth(cb: CallbackQuery, user: User, dialog: EditS10nOneTimeDialog): F[List[ReplyMessage]]

  def removeIsOneTime(cb: CallbackQuery, user: User, dialog: EditS10nOneTimeDialog): F[List[ReplyMessage]]

  def saveIsOneTime(cb: CallbackQuery, data: OneTime, user: User, dialog: EditS10nOneTimeDialog): F[List[ReplyMessage]]

  def onEditS10nOneTimeCb(user: User, cb: CallbackQuery, data: EditS10nOneTime): F[List[ReplyMessage]]

  def saveBillingPeriodUnit(cb: CallbackQuery, data: PeriodUnit, user: User, dialog: EditS10nOneTimeDialog): F[List[ReplyMessage]]

  def saveBillingPeriodDuration(user: User, dialog: EditS10nOneTimeDialog, text: Option[String]): F[RepliesValidated]
}
