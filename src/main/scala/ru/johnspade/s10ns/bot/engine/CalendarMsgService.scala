package ru.johnspade.s10ns.bot.engine

import java.time.{Instant, LocalDate, ZoneOffset}
import java.util.concurrent.TimeUnit

import cats.Applicative.ops._
import cats.Monad
import cats.effect.Clock
import cats.syntax.option._
import ru.johnspade.s10ns.calendar.CalendarService
import telegramium.bots.InlineKeyboardMarkup

class CalendarMsgService[F[_] : Monad : Clock, S <: DialogState](
  private val calendarService: CalendarService
) extends StateMessageService[F, S] {
  protected def createMessageWithCalendar(state: DialogState): F[ReplyMessage] = {
    def generateCalendar: F[InlineKeyboardMarkup] =
      Clock[F].realTime(TimeUnit.MILLISECONDS).map { millis =>
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC)
        calendarService.generateKeyboard(today)
      }

    generateCalendar.map { markup =>
      ReplyMessage(
        text = state.message,
        markup = markup.some
      )
    }
  }
}
