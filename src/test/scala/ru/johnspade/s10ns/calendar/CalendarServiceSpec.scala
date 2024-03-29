package ru.johnspade.s10ns.calendar

import java.time.LocalDate
import java.time.YearMonth

import com.softwaremill.diffx.generic.auto._
import com.softwaremill.diffx.scalatest.DiffShouldMatcher
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import telegramium.bots.InlineKeyboardButton
import telegramium.bots.InlineKeyboardMarkup

import ru.johnspade.s10ns.bot.Calendar
import ru.johnspade.s10ns.bot.DropFirstPayment
import ru.johnspade.s10ns.bot.FirstPayment
import ru.johnspade.s10ns.bot.Ignore
import ru.johnspade.s10ns.bot.Months
import ru.johnspade.s10ns.bot.Years
import ru.johnspade.s10ns.bot.engine.TelegramOps.inlineKeyboardButton

class CalendarServiceSpec extends AnyFlatSpec with Matchers with DiffShouldMatcher {
  private val calendarService = new CalendarService

  "generateYearsKeyboard" should "return a range of years" in {
    val markup = calendarService.generateYearsKeyboard(YearMonth.of(2020, 1))
    markup shouldMatchTo InlineKeyboardMarkup(
      List(
        List(year(2015), year(2016), year(2017), year(2018), year(2019)),
        List(year(2020), year(2021), year(2022), year(2023), year(2024)),
        List(
          inlineKeyboardButton("⬅", Years(YearMonth.of(2010, 1))),
          inlineKeyboardButton("Skip/remove", DropFirstPayment),
          inlineKeyboardButton("➡", Years(YearMonth.of(2030, 1)))
        )
      )
    )
  }

  "generateMonthsKeyboard" should "return months" in {
    val markup = calendarService.generateMonthsKeyboard(2020)
    markup shouldMatchTo InlineKeyboardMarkup(
      List(
        List(month("Jan", 1), month("Feb", 2), month("Mar", 3), month("Apr", 4), month("May", 5), month("Jun", 6)),
        List(month("Jul", 7), month("Aug", 8), month("Sep", 9), month("Oct", 10), month("Nov", 11), month("Dec", 12)),
        List(inlineKeyboardButton("Skip/remove", DropFirstPayment))
      )
    )
  }

  "generateDaysKeyboard" should "return a correct calendar" in {
    val markup = calendarService.generateDaysKeyboard(LocalDate.of(2020, 1, 27))
    markup shouldMatchTo InlineKeyboardMarkup(
      List(
        List(
          inlineKeyboardButton("Jan", Months(2020)),
          inlineKeyboardButton("2020", Years(YearMonth.of(2020, 1)))
        ),
        Array("M", "T", "W", "T", "F", "S", "S").map(ignored).toList,
        List(ignored(" "), ignored(" "), day(1), day(2), day(3), day(4), day(5)),
        List(day(6), day(7), day(8), day(9), day(10), day(11), day(12)),
        List(day(13), day(14), day(15), day(16), day(17), day(18), day(19)),
        List(day(20), day(21), day(22), day(23), day(24), day(25), day(26)),
        List(day(27), day(28), day(29), day(30), day(31), ignored(" "), ignored(" ")),
        List(
          inlineKeyboardButton("⬅", Calendar(LocalDate.of(2019, 12, 1))),
          inlineKeyboardButton("Skip/remove", DropFirstPayment),
          inlineKeyboardButton("➡", Calendar(LocalDate.of(2020, 2, 1)))
        )
      )
    )
  }

  private def ignored(text: String): InlineKeyboardButton = inlineKeyboardButton(text, Ignore)
  private val start                                       = LocalDate.of(2020, 1, 1)
  private def day(n: Int)                 = inlineKeyboardButton(n.toString, FirstPayment(start.withDayOfMonth(n)))
  private def year(n: Int)                = inlineKeyboardButton(n.toString, Calendar(start.withYear(n)))
  private def month(name: String, n: Int) = inlineKeyboardButton(name, Calendar(start.withMonth(n)))
}
