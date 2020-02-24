package ru.johnspade.s10ns.subscription.service

import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.{EditS10nFirstPaymentDate, EditS10nFirstPaymentDateDialog, FirstPayment}
import ru.johnspade.s10ns.user.User
import telegramium.bots.CallbackQuery

trait EditS10n1stPaymentDateDialogService[F[_]] {
  def onEditS10nFirstPaymentDateCb(user: User, cb: CallbackQuery, data: EditS10nFirstPaymentDate): F[List[ReplyMessage]]

  def saveFirstPaymentDate(
    cb: CallbackQuery,
    data: FirstPayment,
    user: User,
    dialog: EditS10nFirstPaymentDateDialog
  ): F[List[ReplyMessage]]

  def removeFirstPaymentDate(user: User, dialog: EditS10nFirstPaymentDateDialog): F[List[ReplyMessage]]
}
