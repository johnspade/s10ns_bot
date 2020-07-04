package ru.johnspade.s10ns.calendar

import cats.effect.Sync
import cats.implicits._
import ru.johnspade.s10ns.bot.Calendar
import ru.johnspade.s10ns.bot.engine.TelegramOps.ackCb
import telegramium.bots.high.Api
import telegramium.bots.high.Methods.editMessageReplyMarkup
import telegramium.bots.high.implicits._
import telegramium.bots.{CallbackQuery, ChatIntId}

class CalendarController[F[_] : Sync](
  private val calendarService: CalendarService
) {
  def calendarCb(cb: CallbackQuery, data: Calendar)(implicit bot: Api[F]): F[Unit] =
    ackCb(cb) *>
      editMessageReplyMarkup(
        chatId = cb.message.map(msg => ChatIntId(msg.chat.id)),
        messageId = cb.message.map(_.messageId),
        replyMarkup = calendarService.generateKeyboard(data.date).some
      ).exec.void
}
