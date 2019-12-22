package ru.johnspade.s10ns.calendar

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import cats.effect.Sync
import ru.johnspade.s10ns.subscription.tags._
import ru.johnspade.s10ns.telegram.{Calendar, FirstPayment, Ignore}
import telegramium.bots.{InlineKeyboardButton, InlineKeyboardMarkup}
import ru.johnspade.s10ns.telegram.TelegramOps.inlineKeyboardButton

class CalendarService[F[_]: Sync] {
  private val monthFormatter = DateTimeFormatter.ofPattern("MMM yyyy")
  private val weekRow = Array("M", "T", "W", "T", "F", "S", "S").map(createIgnoredButton).toList
  private val LengthOfWeek = 7

  private def createIgnoredButton(text: String): InlineKeyboardButton = inlineKeyboardButton(text, Ignore)

  def generateKeyboard(date: LocalDate): F[InlineKeyboardMarkup] = {
    def createPlaceholders(count: Int): List[InlineKeyboardButton] = List.fill(count)(createIgnoredButton(" "))

    val firstDay = date.withDayOfMonth(1)
    val headerRow = List(createIgnoredButton(monthFormatter.format(firstDay)))

    val lengthOfMonth = firstDay.lengthOfMonth

    val shiftStart = firstDay.getDayOfWeek.getValue - 1
    val shiftEnd = LengthOfWeek - firstDay.withDayOfMonth(lengthOfMonth).getDayOfWeek.getValue

    val days = (1 to lengthOfMonth).map { n =>
      val day = firstDay.withDayOfMonth(n)
      inlineKeyboardButton(n.toString, FirstPayment(FirstPaymentDate(day)))
    }
    val calendarRows = (createPlaceholders(shiftStart) ++ days.toList ++ createPlaceholders(shiftEnd))
      .grouped(LengthOfWeek)
      .toList

    val controlsRow = List(
      inlineKeyboardButton("⬅", Calendar(firstDay.minusMonths(1))),
      inlineKeyboardButton("➡", Calendar(firstDay.plusMonths(1)))
    )

    Sync[F].delay(InlineKeyboardMarkup((headerRow :: weekRow :: calendarRows) :+ controlsRow))
  }
}
