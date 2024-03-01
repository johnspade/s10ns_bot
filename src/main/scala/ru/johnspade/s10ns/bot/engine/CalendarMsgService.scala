package ru.johnspade.s10ns.bot.engine

import java.time.LocalDate
import java.time.ZoneOffset

import cats.Monad
import cats.effect.Clock
import cats.syntax.functor._
import cats.syntax.option._

import telegramium.bots.InlineKeyboardMarkup

import ru.johnspade.s10ns.calendar.CalendarService
import ru.johnspade.s10ns.currentTimestamp

class CalendarMsgService[F[_] : Monad : Clock, S <: DialogState](
  private val calendarService: CalendarService
) extends StateMessageService[F, S] {
  protected def createMessageWithCalendar(state: DialogState): F[ReplyMessage] = {
    def generateCalendar: F[InlineKeyboardMarkup] =
      currentTimestamp.map { now =>
        val today = LocalDate.ofInstant(now, ZoneOffset.UTC)
        calendarService.generateDaysKeyboard(today)
      }

    generateCalendar.map { markup =>
      ReplyMessage(
        text = state.message,
        markup = markup.some
      )
    }
  }
}
