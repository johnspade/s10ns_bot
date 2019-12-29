package ru.johnspade.s10ns.subscription

import cats.effect.Sync
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import ru.johnspade.s10ns.subscription.tags.PageNumber
import ru.johnspade.s10ns.telegram.TelegramOps.ackCb
import ru.johnspade.s10ns.telegram.{EditS10n, RemoveS10n, ReplyMessage, S10n, S10ns}
import ru.johnspade.s10ns.user.User
import telegramium.bots.client.{Api, EditMessageReplyMarkupReq, EditMessageTextReq}
import telegramium.bots.{CallbackQuery, ChatIntId, InlineKeyboardMarkup, MarkupInlineKeyboard}

class SubscriptionListController[F[_] : Sync : Logger](
  private val s10nListService: SubscriptionListService[F]
) {
  def subscriptionsCb(user: User, cb: CallbackQuery, data: S10ns)(implicit bot: Api[F]): F[Unit] =
    s10nListService.onSubscriptionsCb(user, cb, data)
      .flatMap(ackCb(cb) *> editMessage(cb, _))

  def removeSubscriptionCb(user: User, cb: CallbackQuery, data: RemoveS10n)(implicit bot: Api[F]): F[Unit] =
    s10nListService.onRemoveSubscriptionCb(user, cb, data)
      .flatMap(ackCb(cb) *> editMessage(cb, _))

  def subscriptionCb(user: User, cb: CallbackQuery, data: S10n)(implicit bot: Api[F]): F[Unit] =
    ackAndEditMsg(cb, s10nListService.onSubcriptionCb(user, cb, data))

  def listCommand(from: User): F[ReplyMessage] = s10nListService.onListCommand(from, PageNumber(0))

  def editS10nCb(cb: CallbackQuery, data: EditS10n)(implicit bot: Api[F]): F[Unit] = {
    def editMarkup(markup: InlineKeyboardMarkup) = {
      val req = EditMessageReplyMarkupReq(
        cb.message.map(msg => ChatIntId(msg.chat.id)),
        cb.message.map(_.messageId),
        replyMarkup = markup.some
      )
      bot.editMessageReplyMarkup(req).void
    }

    s10nListService.onEditS10nCb(cb, data)
      .flatMap {
        case Left(error) => ackCb(cb, error.some)
        case Right(markup) => ackCb(cb) *> editMarkup(markup)
      }
  }

  private def editMessage(cb: CallbackQuery, reply: ReplyMessage)(implicit bot: Api[F]) = {
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

  private def ackAndEditMsg(cb: CallbackQuery, replyF: F[Either[String, ReplyMessage]])(implicit bot: Api[F]) =
    replyF.flatMap {
      case Left(error) => ackCb(cb, error.some)
      case Right(reply) => ackCb(cb) *> editMessage(cb, reply)
    }
}
