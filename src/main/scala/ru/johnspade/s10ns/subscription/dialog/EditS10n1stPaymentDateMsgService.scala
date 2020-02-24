package ru.johnspade.s10ns.subscription.dialog

import cats.Monad
import cats.effect.Clock
import ru.johnspade.s10ns.bot.engine.{CalendarMsgService, ReplyMessage}
import ru.johnspade.s10ns.calendar.CalendarService

class EditS10n1stPaymentDateMsgService[F[_] : Monad : Clock](
  private val calendarService: CalendarService
) extends CalendarMsgService[F, EditS10nFirstPaymentDateDialogState](calendarService) {
  override def createReplyMessage(state: EditS10nFirstPaymentDateDialogState): F[ReplyMessage] =
    state match {
      case EditS10nFirstPaymentDateDialogState.FirstPaymentDate => createMessageWithCalendar(state)
      case _ => super.createReplyMessage(state)
    }
}
