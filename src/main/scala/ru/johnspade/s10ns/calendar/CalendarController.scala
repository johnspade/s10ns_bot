package ru.johnspade.s10ns.calendar

import cats.effect.Sync
import cats.implicits._
import ru.johnspade.s10ns.telegram.CalendarCallbackData
import ru.johnspade.s10ns.telegram.TelegramOps.ackCb
import telegramium.bots.client.{Api, EditMessageReplyMarkupReq}
import telegramium.bots.{CallbackQuery, ChatIntId}

class CalendarController[F[_] : Sync](
  private val calendarService: CalendarService[F]
) {
  def calendarCb(cb: CallbackQuery)(implicit bot: Api[F]): F[Unit] =
    ackCb(cb) *>
      cb.data
        .traverse { data =>
          calendarService.generateKeyboard(CalendarCallbackData.fromString(data).date)
        }
        .flatMap { kb =>
          bot.editMessageReplyMarkup(EditMessageReplyMarkupReq(
            chatId = cb.message.map(msg => ChatIntId(msg.chat.id)),
            messageId = cb.message.map(_.messageId),
            replyMarkup = kb
          ))
        }.void

}
