package ru.johnspade.s10ns.telegram

import cats.Monad
import cats.data.Validated.{Invalid, Valid}
import cats.effect.Sync
import cats.implicits._
import ru.johnspade.s10ns.common.ValidatorNec.ValidationResult
import ru.johnspade.s10ns.subscription.SubscriptionDraft
import ru.johnspade.s10ns.user._
import telegramium.bots.client.{AnswerCallbackQueryReq, Api, SendMessageReq}
import telegramium.bots.{CallbackQuery, ChatIntId, Message}
import ru.johnspade.s10ns.user.tags._

object TelegramOps {
  implicit class TelegramUserOps(val value: telegramium.bots.User) extends AnyVal {
    def toUser(chatId: Option[Long] = None, dialog: Option[DialogType] = None, draft: Option[SubscriptionDraft] = None): User =
      User(
        id = UserId(value.id.toLong),
        firstName = FirstName(value.firstName),
        lastName = value.lastName.map(LastName(_)),
        username = value.username.map(Username(_)),
        chatId = chatId.map(ChatId(_)),
        dialogType = dialog,
        subscriptionDraft = draft
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
}
