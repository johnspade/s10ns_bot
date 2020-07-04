package ru.johnspade.s10ns.bot.engine

import cats.Monad
import cats.data.Validated.{Invalid, Valid}
import cats.effect.{Sync, Timer}
import cats.implicits._
import ru.johnspade.s10ns.bot.ValidatorNec.ValidationResult
import ru.johnspade.s10ns.bot.{CbData, Dialog}
import ru.johnspade.s10ns.user._
import ru.johnspade.s10ns.user.tags._
import telegramium.bots.high.Methods._
import telegramium.bots.high._
import telegramium.bots.high.implicits._
import telegramium.bots.{CallbackQuery, ChatIntId, Message}
import tofu.logging.Logging

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

  def sendReplyMessage[F[_]: Sync: Timer](msg: Message, reply: ReplyMessage)(implicit bot: Api[F]): F[Unit] =
    sendMessage(
      ChatIntId(msg.chat.id),
      reply.text,
      replyMarkup = reply.markup,
      parseMode = reply.parseMode
    ).exec.void

  def ackCb[F[_]: Sync](cb: CallbackQuery, text: Option[String] = None)(implicit bot: Api[F]): F[Unit] =
    answerCallbackQuery(cb.id, text).exec.void

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
    InlineKeyboardButton.callbackData(text, callbackData = cbData.toCsv)

  def clearMarkup[F[_]: Sync](cb: CallbackQuery)(implicit bot: Api[F]): F[Unit] =
    editMessageReplyMarkup(
      cb.message.map(msg => ChatIntId(msg.chat.id)), cb.message.map(_.messageId)
    )
      .exec
      .void

  def handleCallback[F[_]: Sync: Logging: Timer](query: CallbackQuery, replies: List[ReplyMessage])(
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

  def sendReplyMessages[F[_]: Sync: Logging: Timer](msg: Message, replies: List[ReplyMessage])(
    implicit bot: Api[F]
  ): F[Unit] =
    replies.map { reply =>
      sendReplyMessage(msg, reply)
        .void
        .handleErrorWith(e => Logging[F].errorCause(e.getMessage, e)) *>
        Timer[F].sleep(FiniteDuration(1, "second"))
    }
      .sequence_
}
