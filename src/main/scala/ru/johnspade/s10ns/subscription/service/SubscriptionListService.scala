package ru.johnspade.s10ns.subscription.service

import cats.effect.Sync
import cats.implicits._
import cats.{Monad, ~>}
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.{EditS10n, Errors, RemoveS10n, S10n, S10ns}
import ru.johnspade.s10ns.subscription.Subscription
import ru.johnspade.s10ns.subscription.repository.SubscriptionRepository
import ru.johnspade.s10ns.subscription.tags.PageNumber
import ru.johnspade.s10ns.user.User
import ru.johnspade.s10ns.user.tags._
import telegramium.bots.{CallbackQuery, InlineKeyboardMarkup}

class SubscriptionListService[F[_] : Sync, D[_] : Monad](
  private val s10nRepo: SubscriptionRepository[D],
  private val s10nsListService: S10nsListMessageService[F]
)(private implicit val transact: D ~> F) {
  def onSubscriptionsCb(user: User, cb: CallbackQuery, data: S10ns): F[ReplyMessage] = {
    for {
      s10ns <- transact(s10nRepo.getByUserId(user.id))
      reply <- s10nsListService.createSubscriptionsPage(s10ns, data.page, user.defaultCurrency)
    } yield reply
  }

  def onRemoveSubscriptionCb(user: User, cb: CallbackQuery, data: RemoveS10n): F[ReplyMessage] = {
    for {
      _ <- transact(s10nRepo.remove(data.subscriptionId))
      s10ns <- transact(s10nRepo.getByUserId(user.id))
      reply <- s10nsListService.createSubscriptionsPage(s10ns, data.page, user.defaultCurrency)
    } yield reply
  }

  def onSubcriptionCb(user: User, cb: CallbackQuery, data: S10n): F[Either[String, ReplyMessage]] = {
    def checkUserAndGetMessage(subscription: Subscription) =
      Either.cond(
        subscription.userId == user.id,
        s10nsListService.createSubscriptionMessage(user, subscription, data.page),
        Errors.AccessDenied
      )
        .sequence

    for {
      s10nOpt <- transact(s10nRepo.getById(data.subscriptionId))
      replyOpt <- s10nOpt.traverse(checkUserAndGetMessage)
    } yield replyOpt.toRight[String](Errors.NotFound).flatten
  }

  def onListCommand(from: User, page: PageNumber): F[ReplyMessage] =
    transact(s10nRepo.getByUserId(from.id))
      .flatMap {
        s10nsListService.createSubscriptionsPage(_, page, from.defaultCurrency)
      }

  def onEditS10nCb(cb: CallbackQuery, data: EditS10n): F[Either[String, InlineKeyboardMarkup]] = {
    def checkUserAndGetMarkup(subscription: Subscription) =
      Either.cond(
        subscription.userId == UserId(cb.from.id.toLong),
        s10nsListService.createEditS10nMarkup(subscription, data.page),
        Errors.AccessDenied
      )

    transact(s10nRepo.getById(data.subscriptionId))
      .map {
        _.map(checkUserAndGetMarkup)
          .toRight[String](Errors.NotFound)
          .flatten
      }
  }
}
