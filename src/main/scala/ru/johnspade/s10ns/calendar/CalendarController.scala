package ru.johnspade.s10ns.calendar

import cats.effect.Sync
import cats.implicits._
import ru.johnspade.s10ns.bot.engine.TelegramOps.ackCb
import ru.johnspade.s10ns.bot.{Calendar, Months, Years}
import telegramium.bots.high.Api
import telegramium.bots.high.Methods.editMessageReplyMarkup
import telegramium.bots.high.implicits._
import telegramium.bots.{CallbackQuery, ChatIntId, InlineKeyboardMarkup}

class CalendarController[F[_]: Sync](
  private val calendarService: CalendarService
) {
  def calendarCb(cb: CallbackQuery, data: Calendar)(implicit bot: Api[F]): F[Unit] =
    editMarkup(cb, calendarService.generateDaysKeyboard(data.date))

  def yearsCb(cb: CallbackQuery, data: Years)(implicit bot: Api[F]): F[Unit] =
    editMarkup(cb, calendarService.generateYearsKeyboard(data.yearMonth))

  def monthsCb(cb: CallbackQuery, data: Months)(implicit bot: Api[F]): F[Unit] =
    editMarkup(cb, calendarService.generateMonthsKeyboard(data.year))

  private def editMarkup(cb: CallbackQuery, markup: InlineKeyboardMarkup)(implicit bot: Api[F]) =
    ackCb(cb) *>
      editMessageReplyMarkup(
        chatId = cb.message.map(msg => ChatIntId(msg.chat.id)),
        messageId = cb.message.map(_.messageId),
        replyMarkup = markup.some
      ).exec.void
}
