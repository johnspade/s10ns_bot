package ru.johnspade.s10ns.telegram

import cats.Monad
import cats.data.Validated.{Invalid, Valid}
import cats.effect.Sync
import cats.implicits._
import ru.johnspade.s10ns.common.ValidatorNec.ValidationResult
import ru.johnspade.s10ns.user._
import ru.johnspade.s10ns.user.tags._
import telegramium.bots.client.{AnswerCallbackQueryReq, Api, SendMessageReq}
import telegramium.bots.{CallbackQuery, ChatIntId, Message}

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

  def ackCb[F[_] : Sync : Monad](cb: CallbackQuery, text: Option[String] = None)(implicit bot: Api[F]): F[Unit] =
    bot.answerCallbackQuery(AnswerCallbackQueryReq(cb.id, text)).void

  def toReplyMessage(reply: ValidationResult[ReplyMessage]): ReplyMessage =
    reply match {
      case Valid(message) => message
      case Invalid(errors) => ReplyMessage(errors.map(_.errorMessage).mkString_("\n"))
    }

  def toReplyMessage(replies: ValidationResult[List[ReplyMessage]]): List[ReplyMessage] =
    replies match {
      case Valid(messages) => messages
      case Invalid(errors) => List(ReplyMessage(errors.map(_.errorMessage).mkString_("\n")))
    }

  def singleTextMessage(text: String): List[ReplyMessage] = List(ReplyMessage(text))
}
