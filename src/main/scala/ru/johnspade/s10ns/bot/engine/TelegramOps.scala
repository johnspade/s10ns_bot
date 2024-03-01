package ru.johnspade.s10ns.bot.engine

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.MILLISECONDS

import cats.Functor
import cats.Monad
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.effect.Temporal
import cats.implicits._

import telegramium.bots.CallbackQuery
import telegramium.bots.ChatIntId
import telegramium.bots.InlineKeyboardButton
import telegramium.bots.Message
import telegramium.bots.high.Api
import telegramium.bots.high.Methods._
import telegramium.bots.high.implicits._
import telegramium.bots.high.keyboards.InlineKeyboardButtons
import tofu.logging.Logging

import ru.johnspade.s10ns.bot.CbData
import ru.johnspade.s10ns.bot.Dialog
import ru.johnspade.s10ns.bot.ValidatorNec.ValidationResult
import ru.johnspade.s10ns.user._
import ru.johnspade.s10ns.user.tags._

object TelegramOps {
  implicit class TelegramUserOps(val value: telegramium.bots.User) extends AnyVal {
    def toUser(chatId: Option[Long] = None, dialog: Option[Dialog] = None): User =
      User(
        id = UserId(value.id),
        firstName = FirstName(value.firstName),
        chatId = chatId.map(ChatId(_)),
        dialog = dialog
      )
  }

  def sendReplyMessage[F[_]](msg: Message, reply: ReplyMessage)(implicit bot: Api[F], F: Functor[F]): F[Unit] =
    sendMessage(
      ChatIntId(msg.chat.id),
      reply.text,
      replyMarkup = reply.markup,
      parseMode = reply.parseMode,
      disableWebPagePreview = reply.disableWebPagePreview
    ).exec.void

  def ackCb[F[_]](cb: CallbackQuery, text: Option[String] = None)(implicit bot: Api[F], F: Functor[F]): F[Unit] =
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
    InlineKeyboardButtons.callbackData(text, callbackData = cbData.toCsv)

  def clearMarkup[F[_]](cb: CallbackQuery)(implicit bot: Api[F], F: Functor[F]): F[Unit] =
    editMessageReplyMarkup(
      cb.message.map(msg => ChatIntId(msg.chat.id)), cb.message.map(_.messageId)
    )
      .exec
      .void

  def handleCallback[F[_]: Logging: Temporal](query: CallbackQuery, replies: List[ReplyMessage])(
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

  def sendReplyMessages[F[_]: Logging: Temporal](msg: Message, replies: List[ReplyMessage])(
    implicit bot: Api[F]
  ): F[Unit] =
    replies.map { reply =>
      sendReplyMessage[F](msg, reply)
        .void
        .handleErrorWith(e => Logging[F].errorCause(e.getMessage, e)) *>
        Temporal[F].sleep(FiniteDuration(400, MILLISECONDS))
    }
      .sequence_
}
