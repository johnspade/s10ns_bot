package ru.johnspade.s10ns.calendar

import cats.effect.Sync
import cats.implicits._
import ru.johnspade.s10ns.CbDataRoutes
import ru.johnspade.s10ns.bot.engine.TelegramOps.ackCb
import ru.johnspade.s10ns.bot.engine.callbackqueries.CallbackQueryRoutes
import ru.johnspade.s10ns.bot.{Calendar, CallbackQueryController, CbData, Months, Years}
import telegramium.bots.high.Api
import telegramium.bots.high.Methods.editMessageReplyMarkup
import telegramium.bots.high.implicits._
import telegramium.bots.{CallbackQuery, ChatIntId, InlineKeyboardMarkup}

class CalendarController[F[_]: Sync](
  private val calendarService: CalendarService
)(implicit bot: Api[F]) extends CallbackQueryController[F] {
  override val routes: CbDataRoutes[F] = CallbackQueryRoutes.of[CbData, F] {
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
