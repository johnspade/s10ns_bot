package ru.johnspade.s10ns.subscription.dialog

import cats.Monad
import cats.effect.Clock

import ru.johnspade.s10ns.bot.engine.CalendarMsgService
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.calendar.CalendarService

class CreateS10nMsgService[F[_]: Monad: Clock](
    private val calendarService: CalendarService
) extends CalendarMsgService[F, CreateS10nDialogState](calendarService) {
  override def createReplyMessage(state: CreateS10nDialogState): F[ReplyMessage] = {
    state match {
      case CreateS10nDialogState.FirstPaymentDate => createMessageWithCalendar(state)
      case _                                      => super.createReplyMessage(state)
    }
  }
}
