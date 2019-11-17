package ru.johnspade.s10ns.subscription

import cats.Monad
import cats.effect.Sync
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import ru.johnspade.s10ns.common.Errors
import ru.johnspade.s10ns.telegram.ReplyMessage
import ru.johnspade.s10ns.telegram.TelegramOps.{ackCb, sendReplyMessage, toReplyMessage}
import ru.johnspade.s10ns.user.User
import telegramium.bots.client.{Api, EditMessageReplyMarkupReq}
import telegramium.bots.{CallbackQuery, ChatIntId, Message}

class CreateS10nDialogController[F[_] : Sync : Logger](
  private val createS10nDialogService: CreateS10nDialogService[F]
) {
  def createCommand(user: User): F[ReplyMessage] = createS10nDialogService.onCreateCommand(user)

  def createWithDefaultCurrencyCommand(user: User): F[ReplyMessage] =
    createS10nDialogService.onCreateWithDefaultCurrencyCommand(user)

  def message(user: User, message: Message): F[ReplyMessage] =
    user.subscriptionDialogState.flatMap { state =>
      if (createS10nDialogService.saveDraft.isDefinedAt(user, state, message.text))
        createS10nDialogService.saveDraft(user, state, message.text).map(toReplyMessage).some
      else
        Option.empty[F[ReplyMessage]]
    } getOrElse Sync[F].pure(ReplyMessage(Errors.default))

  def billingPeriodUnitCb(cb: CallbackQuery)(implicit bot: Api[F]): F[Unit] =
    clearMarkup(cb) *> handleCallback(cb, createS10nDialogService.onBillingPeriodUnitCb)

  def isOneTimeCb(cb: CallbackQuery)(implicit bot: Api[F]): F[Unit] =
    clearMarkup(cb) *> handleCallback(cb, createS10nDialogService.onIsOneTimeCallback)

  def firstPaymentDateCb(cb: CallbackQuery)(implicit bot: Api[F]): F[Unit] =
    clearMarkup(cb) *> handleCallback(cb, createS10nDialogService.onFirstPaymentDateCallback)

  private def clearMarkup(cb: CallbackQuery)(implicit bot: Api[F]) =
    bot.editMessageReplyMarkup(
      EditMessageReplyMarkupReq(cb.message.map(msg => ChatIntId(msg.chat.id)), cb.message.map(_.messageId))
    )
      .void

  private def handleCallback(query: CallbackQuery, handler: CallbackQuery => F[Either[String, ReplyMessage]])(
    implicit bot: Api[F]
  ): F[Unit] = {
    val cb = query.copy(data = query.data.map(_.replaceFirst("^.+?\\u001D", "")))

    def sendReply(replyEither: Either[String, ReplyMessage]) =
      cb.message
        .flatMap { msg =>
          replyEither.toOption.map(sendReplyMessage(msg, _))
        }
        .sequence

    def replyToCb(replyEither: Either[String, ReplyMessage]) =
      ackCb(cb, replyEither.left.toOption) *> sendReply(replyEither)

    for {
      replyEither <- handler(cb)
      reply <- replyToCb(replyEither)
    } yield reply getOrElse Monad[F].unit
  }
}
