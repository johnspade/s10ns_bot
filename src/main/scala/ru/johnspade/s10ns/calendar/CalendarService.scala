package ru.johnspade.s10ns.calendar

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import ru.johnspade.s10ns.bot.engine.TelegramOps.inlineKeyboardButton
import ru.johnspade.s10ns.bot.{Calendar, DropFirstPayment, FirstPayment, Ignore}
import ru.johnspade.s10ns.subscription.tags._
import telegramium.bots.{InlineKeyboardButton, InlineKeyboardMarkup}

class CalendarService {
  private val monthFormatter = DateTimeFormatter.ofPattern("MMM yyyy")
  private val weekRow = Array("M", "T", "W", "T", "F", "S", "S").map(createIgnoredButton).toList
  private val LengthOfWeek = 7

  private def createIgnoredButton(text: String): InlineKeyboardButton = inlineKeyboardButton(text, Ignore)

  def generateKeyboard(date: LocalDate): InlineKeyboardMarkup = {
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
      inlineKeyboardButton("Skip/remove", DropFirstPayment),
      inlineKeyboardButton("➡", Calendar(firstDay.plusMonths(1)))
    )

    InlineKeyboardMarkup((headerRow :: weekRow :: calendarRows) :+ controlsRow)
  }
}
