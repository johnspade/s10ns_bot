package ru.johnspade.s10ns.calendar

import cats.effect.Sync
import cats.implicits.*

import ru.johnspade.tgbot.callbackqueries.CallbackQueryRoutes
import telegramium.bots.CallbackQuery
import telegramium.bots.ChatIntId
import telegramium.bots.InlineKeyboardMarkup
import telegramium.bots.high.Api
import telegramium.bots.high.Methods.editMessageReplyMarkup
import telegramium.bots.high.implicits.*

import ru.johnspade.s10ns.CbDataRoutes
import ru.johnspade.s10ns.bot.Calendar
import ru.johnspade.s10ns.bot.CallbackQueryController
import ru.johnspade.s10ns.bot.Months
import ru.johnspade.s10ns.bot.Years
import ru.johnspade.s10ns.bot.engine.TelegramOps.ackCb
import ru.johnspade.s10ns.bot.engine.TelegramOps.toMessage

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

  private def editMarkup(cb: CallbackQuery, markup: InlineKeyboardMarkup)(using bot: Api[F]) =
    ackCb(cb) *>
      cb.message
        .flatMap:
          _.toMessage
        .`traverse_`: msg =>
          editMessageReplyMarkup(
            chatId = ChatIntId(msg.chat.id).some,
            messageId = msg.messageId.some,
            replyMarkup = markup.some
          ).exec.void
}
