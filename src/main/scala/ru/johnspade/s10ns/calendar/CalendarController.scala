package ru.johnspade.s10ns.calendar

import cats.effect.Sync
import cats.implicits._

import ru.johnspade.tgbot.callbackqueries.CallbackQueryRoutes
import telegramium.bots.CallbackQuery
import telegramium.bots.ChatIntId
import telegramium.bots.InlineKeyboardMarkup
import telegramium.bots.high.Api
import telegramium.bots.high.Methods.editMessageReplyMarkup
import telegramium.bots.high.implicits._

import ru.johnspade.s10ns.CbDataRoutes
import ru.johnspade.s10ns.bot.Calendar
import ru.johnspade.s10ns.bot.CallbackQueryController
import ru.johnspade.s10ns.bot.CbData
import ru.johnspade.s10ns.bot.Months
import ru.johnspade.s10ns.bot.Years
import ru.johnspade.s10ns.bot.engine.TelegramOps.ackCb

class CalendarController[F[_]: Sync](
    private val calendarService: CalendarService
)(implicit bot: Api[F])
    extends CallbackQueryController[F] {
  override val routes: CbDataRoutes[F] = CallbackQueryRoutes.of {
    case (data: Calendar) in cb =>
      editMarkup(cb, calendarService.generateDaysKeyboard(data.date))

    case (data: Years) in cb =>
      editMarkup(cb, calendarService.generateYearsKeyboard(data.yearMonth))

    case (data: Months) in cb =>
      editMarkup(cb, calendarService.generateMonthsKeyboard(data.year))
  }

  private def editMarkup(cb: CallbackQuery, markup: InlineKeyboardMarkup)(implicit bot: Api[F]) =
    ackCb(cb) *>
      editMessageReplyMarkup(
        chatId = cb.message.map(msg => ChatIntId(msg.chat.id)),
        messageId = cb.message.map(_.messageId),
        replyMarkup = markup.some
      ).exec.void
}
