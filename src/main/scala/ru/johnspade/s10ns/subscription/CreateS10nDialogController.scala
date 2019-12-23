package ru.johnspade.s10ns.subscription

import cats.Monad
import cats.effect.Sync
import cats.implicits._
import ru.johnspade.s10ns.common.Errors
import ru.johnspade.s10ns.telegram.TelegramOps.{ackCb, sendReplyMessage, toReplyMessage}
import ru.johnspade.s10ns.telegram.{FirstPayment, OneTime, PeriodUnit, ReplyMessage}
import ru.johnspade.s10ns.user.{CreateS10nDialog, User}
import telegramium.bots.client.{Api, EditMessageReplyMarkupReq}
import telegramium.bots.{CallbackQuery, ChatIntId, Message}

class CreateS10nDialogController[F[_] : Sync](
  private val createS10nDialogService: CreateS10nDialogService[F]
) {
  def createCommand(user: User): F[ReplyMessage] = createS10nDialogService.onCreateCommand(user)

  def createWithDefaultCurrencyCommand(user: User): F[ReplyMessage] =
    createS10nDialogService.onCreateWithDefaultCurrencyCommand(user)

  def message(user: User, dialog: CreateS10nDialog, message: Message): F[List[ReplyMessage]] =
    createS10nDialogService.saveDraft
      .lift
      .apply(user, dialog, message.text)
      .map(_.map(toReplyMessage))
      .getOrElse(Sync[F].pure(ReplyMessage(Errors.default)))
      .map(List(_))

  def billingPeriodUnitCb(cb: CallbackQuery, data: PeriodUnit)(implicit bot: Api[F]): F[Unit] =
    clearMarkup(cb) *> handleCallback(cb, createS10nDialogService.onBillingPeriodUnitCb(cb, data))

  def isOneTimeCb(cb: CallbackQuery, data: OneTime)(implicit bot: Api[F]): F[Unit] =
    clearMarkup(cb) *> handleCallback(cb, createS10nDialogService.onIsOneTimeCallback(cb, data))

  def firstPaymentDateCb(cb: CallbackQuery, data: FirstPayment)(implicit bot: Api[F]): F[Unit] =
    clearMarkup(cb) *> handleCallback(cb, createS10nDialogService.onFirstPaymentDateCallback(cb, data))

  private def clearMarkup(cb: CallbackQuery)(implicit bot: Api[F]) =
    bot.editMessageReplyMarkup(
      EditMessageReplyMarkupReq(cb.message.map(msg => ChatIntId(msg.chat.id)), cb.message.map(_.messageId))
    )
      .void

  private def handleCallback(query: CallbackQuery, replyEitherF: F[Either[String, ReplyMessage]])(
    implicit bot: Api[F]
  ): F[Unit] = {
    def sendReply(replyEither: Either[String, ReplyMessage]) =
      query.message
        .flatMap { msg =>
          replyEither.toOption.map(sendReplyMessage(msg, _))
        }
        .sequence

    def replyToCb(replyEither: Either[String, ReplyMessage]) =
      ackCb(query, replyEither.left.toOption) *> sendReply(replyEither)

    for {
      replyEither <- replyEitherF
      reply <- replyToCb(replyEither)
    } yield reply getOrElse Monad[F].unit
  }
}
