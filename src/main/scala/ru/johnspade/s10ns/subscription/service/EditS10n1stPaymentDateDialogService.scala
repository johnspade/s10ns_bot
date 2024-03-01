package ru.johnspade.s10ns.subscription.service

import ru.johnspade.s10ns.bot.EditS10nFirstPaymentDate
import ru.johnspade.s10ns.bot.EditS10nFirstPaymentDateDialog
import ru.johnspade.s10ns.bot.FirstPayment
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.user.User

trait EditS10n1stPaymentDateDialogService[F[_]] {
  def onEditS10nFirstPaymentDateCb(user: User, data: EditS10nFirstPaymentDate): F[List[ReplyMessage]]

  def saveFirstPaymentDate(data: FirstPayment, user: User, dialog: EditS10nFirstPaymentDateDialog): F[List[ReplyMessage]]

  def removeFirstPaymentDate(user: User, dialog: EditS10nFirstPaymentDateDialog): F[List[ReplyMessage]]
}
