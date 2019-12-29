package ru.johnspade.s10ns.telegram

import cats.Monad
import cats.data.Validated.{Invalid, Valid}
import cats.effect.{Sync, Timer}
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import ru.johnspade.s10ns.common.ValidatorNec.ValidationResult
import ru.johnspade.s10ns.user._
import ru.johnspade.s10ns.user.tags._
import telegramium.bots.client.{AnswerCallbackQueryReq, Api, EditMessageReplyMarkupReq, SendMessageReq}
import telegramium.bots.{CallbackQuery, ChatIntId, InlineKeyboardButton, Message}

import scala.concurrent.duration.FiniteDuration

object TelegramOps {
  implicit class TelegramUserOps(val value: telegramium.bots.User) extends AnyVal {
    def toUser(chatId: Option[Long] = None, dialog: Option[Dialog] = None): User =
      User(
        id = UserId(value.id.toLong),
        firstName = FirstName(value.firstName),
        chatId = chatId.map(ChatId(_)),
        dialog = dialog
      )
  }

  def sendReplyMessage[F[_] : Sync](msg: Message, reply: ReplyMessage)(implicit bot: Api[F]): F[Unit] =
    bot.sendMessage(SendMessageReq(ChatIntId(msg.chat.id), reply.text, replyMarkup = reply.markup))
      .void

  def ackCb[F[_] : Sync](cb: CallbackQuery, text: Option[String] = None)(implicit bot: Api[F]): F[Unit] =
    bot.answerCallbackQuery(AnswerCallbackQueryReq(cb.id, text)).void

  def toReplyMessage(reply: ValidationResult[ReplyMessage]): ReplyMessage =
    reply match {
      case Valid(message) => message
      case Invalid(errors) => ReplyMessage(errors.map(_.errorMessage).mkString_("\n"))
    }

  def toReplyMessages(replies: ValidationResult[List[ReplyMessage]]): List[ReplyMessage] =
    replies match {
      case Valid(messages) => messages
      case Invalid(errors) => List(ReplyMessage(errors.map(_.errorMessage).mkString_("\n")))
    }

  def singleTextMessage(text: String): List[ReplyMessage] = List(ReplyMessage(text))

  def inlineKeyboardButton(text: String, cbData: CbData): InlineKeyboardButton =
    InlineKeyboardButton(text, callbackData = cbData.toCsv.some)

  def singleInlineKeyboardButton(text: String, cbData: CbData): List[List[InlineKeyboardButton]] =
    List(List(inlineKeyboardButton(text, cbData)))

  def clearMarkup[F[_] : Sync](cb: CallbackQuery)(implicit bot: Api[F]): F[Unit] =
    bot.editMessageReplyMarkup(
      EditMessageReplyMarkupReq(cb.message.map(msg => ChatIntId(msg.chat.id)), cb.message.map(_.messageId))
    )
      .void

  def handleCallback[F[_] : Sync : Logger : Timer](query: CallbackQuery, replies: List[ReplyMessage])(
    implicit bot: Api[F]
  ): F[Unit] = {
    def sendReplies() =
      query.message
        .map {
          sendReplyMessages(_, replies)
        }
        .getOrElse(Monad[F].unit)

    ackCb(query) *> sendReplies()
  }

  def sendReplyMessages[F[_] : Sync : Logger : Timer](msg: Message, replies: List[ReplyMessage])(
    implicit bot: Api[F]
  ): F[Unit] =
    replies.map { reply =>
      sendReplyMessage(msg, reply)
        .handleErrorWith(e => Logger[F].error(e)(e.getMessage)) *>
        Timer[F].sleep(FiniteDuration(1, "second"))
    }
      .sequence_
}
