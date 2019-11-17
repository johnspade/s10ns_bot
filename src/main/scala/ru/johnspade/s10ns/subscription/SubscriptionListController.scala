package ru.johnspade.s10ns.subscription

import cats.effect.Sync
import cats.implicits._
import ru.johnspade.s10ns.common.PageNumber
import ru.johnspade.s10ns.telegram.ReplyMessage
import ru.johnspade.s10ns.telegram.TelegramOps.ackCb
import ru.johnspade.s10ns.user.User
import telegramium.bots.client.{Api, EditMessageTextReq}
import telegramium.bots.{CallbackQuery, ChatIntId, InlineKeyboardMarkup, MarkupInlineKeyboard}

class SubscriptionListController[F[_] : Sync](
  private val s10nListService: SubscriptionListService[F]
) {
  def subscriptionsCb(cb: CallbackQuery)(implicit bot: Api[F]): F[Unit] =
    ackAndEditMsg(cb, s10nListService.onSubscriptionsCb(cb))

  def removeSubscriptionCb(cb: CallbackQuery)(implicit bot: Api[F]): F[Unit] =
    ackAndEditMsg(cb, s10nListService.onRemoveSubscriptionCb(cb))

  def subscriptionCb(cb: CallbackQuery)(implicit bot: Api[F]): F[Unit] =
    ackAndEditMsg(cb, s10nListService.onSubcriptionCb(cb))

  def listCommand(from: User): F[ReplyMessage] = s10nListService.onListCommand(from, PageNumber(0))

  private def ackAndEditMsg(cb: CallbackQuery, replyF: F[Either[String, ReplyMessage]])(implicit bot: Api[F]) = {
    def editMessage(reply: ReplyMessage) = {
      val markup = reply.markup match {
        case Some(MarkupInlineKeyboard(markup)) => markup.some
        case _ => Option.empty[InlineKeyboardMarkup]
      }
      val editMessageTextReq = EditMessageTextReq(
        cb.message.map(msg => ChatIntId(msg.chat.id)),
        cb.message.map(_.messageId),
        text = reply.text,
        replyMarkup = markup
      )
      bot.editMessageText(editMessageTextReq).void
    }

    replyF.flatMap {
      case Left(error) => ackCb(cb, error.some)
      case Right(reply) => ackCb(cb) *> editMessage(reply)
    }
  }
}
