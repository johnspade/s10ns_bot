package ru.johnspade.s10ns.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

import telegramium.bots.InlineKeyboardButton
import telegramium.bots.InlineKeyboardMarkup

import ru.johnspade.s10ns.bot.Calendar
import ru.johnspade.s10ns.bot.DropFirstPayment
import ru.johnspade.s10ns.bot.FirstPayment
import ru.johnspade.s10ns.bot.Ignore
import ru.johnspade.s10ns.bot.Months
import ru.johnspade.s10ns.bot.Years
import ru.johnspade.s10ns.bot.engine.TelegramOps.inlineKeyboardButton

class CalendarService {
  private val weekRow = DayOfWeek
    .values()
    .map { d =>
      val narrowName = d.getDisplayName(TextStyle.NARROW_STANDALONE, Locale.US)
      createIgnoredButton(narrowName)
    }
    .toList
  private val LengthOfWeek = 7

  // 5 + current year + 4 = 10
  private val PastYearsCount   = 5
  private val FutureYearsCount = 4

  def generateYearsKeyboard(yearMonth: YearMonth): InlineKeyboardMarkup = {
    val month = yearMonth.getMonth
    val years = yearMonth.getYear - PastYearsCount to yearMonth.getYear + FutureYearsCount
    val yearRows = years
      .map { year =>
        inlineKeyboardButton(year.toString, Calendar(LocalDate.of(year, month, 1)))
      }
      .toList
      .grouped(5)
      .toList
      .reverse
    val controlsRow = List(
      inlineKeyboardButton("⬅", Years(YearMonth.of(years.previousRange.last - FutureYearsCount, month))),
      inlineKeyboardButton("Skip/remove", DropFirstPayment),
      inlineKeyboardButton("➡", Years(YearMonth.of(years.nextRange.last - FutureYearsCount, month)))
    )
    InlineKeyboardMarkup((controlsRow +: yearRows).reverse)
  }

  def generateMonthsKeyboard(year: Int): InlineKeyboardMarkup = {
    val monthRows = Month
      .values()
      .toList
      .map { month =>
        val shortName = month.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.US)
        inlineKeyboardButton(shortName, Calendar(LocalDate.of(year, month, 1)))
      }
      .grouped(6)
      .toList
      .reverse
    val controlsRow = List(inlineKeyboardButton("Skip/remove", DropFirstPayment))
    InlineKeyboardMarkup((controlsRow +: monthRows).reverse)
  }

  def generateDaysKeyboard(date: LocalDate): InlineKeyboardMarkup = {
    def createPlaceholders(count: Int): List[InlineKeyboardButton] = List.fill(count)(createIgnoredButton(" "))

    val firstDay = date.withDayOfMonth(1)
    val headerRow = List(
      inlineKeyboardButton(
        firstDay.getMonth.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.US),
        Months(firstDay.getYear)
      ),
      inlineKeyboardButton(firstDay.getYear.toString, Years(YearMonth.from(firstDay)))
    )

    val lengthOfMonth = firstDay.lengthOfMonth

    val shiftStart = firstDay.getDayOfWeek.getValue - 1
    val shiftEnd   = LengthOfWeek - firstDay.withDayOfMonth(lengthOfMonth).getDayOfWeek.getValue

    val days = (1 to lengthOfMonth).map { n =>
      val day = firstDay.withDayOfMonth(n)
      inlineKeyboardButton(n.toString, FirstPayment(day))
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

  private def createIgnoredButton(text: String): InlineKeyboardButton = inlineKeyboardButton(text, Ignore)
}
