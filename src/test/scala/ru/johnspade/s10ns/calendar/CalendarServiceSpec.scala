package ru.johnspade.s10ns.calendar

import java.time.LocalDate

import com.softwaremill.diffx.scalatest.DiffMatcher
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ru.johnspade.s10ns.bot.engine.TelegramOps.inlineKeyboardButton
import ru.johnspade.s10ns.bot.{Calendar, DropFirstPayment, FirstPayment, Ignore}
import ru.johnspade.s10ns.subscription.tags.FirstPaymentDate
import telegramium.bots.{InlineKeyboardButton, InlineKeyboardMarkup}

class CalendarServiceSpec extends AnyFlatSpec with Matchers with DiffMatcher {
  private val calendarService = new CalendarService

  "generateKeyboard" should "return a correct calendar" in {
    val markup = calendarService.generateKeyboard(LocalDate.of(2020, 1, 27))
    markup should matchTo(InlineKeyboardMarkup(List(
      List(ignored("Jan 2020")),
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
    )))
  }

  private def ignored(text: String): InlineKeyboardButton = inlineKeyboardButton(text, Ignore)
  private val start = LocalDate.of(2020, 1, 1)
  private def day(n: Int) = inlineKeyboardButton(n.toString, FirstPayment(FirstPaymentDate(start.withDayOfMonth(n))))
}
