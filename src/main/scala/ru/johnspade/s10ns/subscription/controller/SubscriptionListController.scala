package ru.johnspade.s10ns.subscription.controller

import cats.Defer
import cats.Monad
import cats.implicits._

import ru.johnspade.tgbot.callbackqueries.CallbackQueryContextRoutes
import telegramium.bots.CallbackQuery
import telegramium.bots.ChatIntId
import telegramium.bots.InlineKeyboardMarkup
import telegramium.bots.high.Methods._
import telegramium.bots.high._
import telegramium.bots.high.implicits._

import ru.johnspade.s10ns.CbDataUserRoutes
import ru.johnspade.s10ns.bot.CallbackQueryUserController
import ru.johnspade.s10ns.bot.EditS10n
import ru.johnspade.s10ns.bot.Notify
import ru.johnspade.s10ns.bot.RemoveS10n
import ru.johnspade.s10ns.bot.S10n
import ru.johnspade.s10ns.bot.S10ns
import ru.johnspade.s10ns.bot.S10nsPeriod
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.engine.TelegramOps.ackCb
import ru.johnspade.s10ns.subscription.service.SubscriptionListService
import ru.johnspade.s10ns.subscription.tags.PageNumber
import ru.johnspade.s10ns.user.User

class SubscriptionListController[F[_]: Monad: Defer](
  private val s10nListService: SubscriptionListService[F]
)(implicit bot: Api[F]) extends CallbackQueryUserController[F] {
  def listCommand(from: User): F[ReplyMessage] = s10nListService.onListCommand(from, PageNumber(0))

  override val routes: CbDataUserRoutes[F] = CallbackQueryContextRoutes.of {
    case (data: S10ns) in cb as user =>
      s10nListService.onSubscriptionsCb(user, cb, data)
        .flatMap(ackCb(cb) *> editMessage(cb, _))

    case (data: S10nsPeriod) in cb as user =>
      s10nListService.onS10nsPeriodCb(user, cb, data)
        .flatMap(ackCb(cb) *> editMessage(cb, _))

    case (data: RemoveS10n) in cb as user =>
      s10nListService.onRemoveSubscriptionCb(user, cb, data)
        .flatMap(ackCb(cb) *> editMessage(cb, _))

    case (data: S10n) in cb as user =>
      ackAndEditMsg(cb, s10nListService.onSubcriptionCb(user, cb, data))

    case (data: EditS10n) in cb as _ =>
      s10nListService.onEditS10nCb(cb, data)
        .flatMap {
          case Left(error) => ackCb(cb, error.some)
          case Right(markup) => ackCb(cb) *> editMarkup(cb, markup)
        }

    case (data: Notify) in cb as user =>
      s10nListService.onNotifyCb(user, cb, data)
        .flatMap {
          case Left(error) => ackCb(cb, error.some)
          case Right(markup) => ackCb(cb) *> editMarkup(cb, markup)
        }
  }

  private def editMessage(cb: CallbackQuery, reply: ReplyMessage)(implicit bot: Api[F]) = {
    val markup = reply.markup match {
      case Some(inlineKeyboard @ InlineKeyboardMarkup(_)) => inlineKeyboard.some
      case _ => Option.empty[InlineKeyboardMarkup]
    }
    editMessageText(
      cb.message.map(msg => ChatIntId(msg.chat.id)),
      cb.message.map(_.messageId),
      text = reply.text,
      replyMarkup = markup,
      parseMode = reply.parseMode
    ).exec.void
  }

  private def ackAndEditMsg(cb: CallbackQuery, replyF: F[Either[String, ReplyMessage]])(implicit bot: Api[F]) =
    replyF.flatMap {
      case Left(error) => ackCb(cb, error.some)
      case Right(reply) => ackCb(cb) *> editMessage(cb, reply)
    }

  private def editMarkup(cb: CallbackQuery, markup: InlineKeyboardMarkup)(implicit bot: Api[F]) =
    editMessageReplyMarkup(
      cb.message.map(msg => ChatIntId(msg.chat.id)),
      cb.message.map(_.messageId),
      replyMarkup = markup.some
    ).exec.void
}
