package ru.johnspade.s10ns.subscription

import cats.effect.Sync
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import ru.johnspade.s10ns.common.Errors
import ru.johnspade.s10ns.common.tags._
import ru.johnspade.s10ns.telegram.TelegramOps.TelegramUserOps
import ru.johnspade.s10ns.telegram.{EditS10nCbData, RemoveSubscriptionCbData, ReplyMessage, SubscriptionCbData, SubscriptionsCbData}
import ru.johnspade.s10ns.user.tags._
import ru.johnspade.s10ns.user.{User, UserRepository}
import telegramium.bots.{CallbackQuery, InlineKeyboardMarkup}

class SubscriptionListService[F[_] : Sync](
  private val userRepo: UserRepository,
  private val s10nRepo: SubscriptionRepository,
  private val s10nsListService: S10nsListMessageService[F]
)(private implicit val xa: Transactor[F]) {
  def onSubscriptionsCb(cb: CallbackQuery, data: SubscriptionsCbData): F[ReplyMessage] = {
    val tgUser = cb.from.toUser()
    for {
      user <- userRepo.getOrCreate(tgUser).transact(xa)
      s10ns <- s10nRepo.getByUserId(user.id).transact(xa)
      reply <- s10nsListService.createSubscriptionsPage(s10ns, data.page, user.defaultCurrency)
    } yield reply
  }

  def onRemoveSubscriptionCb(cb: CallbackQuery, data: RemoveSubscriptionCbData): F[ReplyMessage] = {
    val tgUser = cb.from.toUser()
    for {
      _ <- s10nRepo.remove(data.subscriptionId).transact(xa)
      user <- userRepo.getOrCreate(tgUser).transact(xa)
      s10ns <- s10nRepo.getByUserId(user.id).transact(xa)
      reply <- s10nsListService.createSubscriptionsPage(s10ns, data.page, user.defaultCurrency)
    } yield reply
  }

  def onSubcriptionCb(cb: CallbackQuery, data: SubscriptionCbData): F[Either[String, ReplyMessage]] = {
    def checkUserAndGetMessage(user: User, subscription: Subscription) =
      Either.cond(
        subscription.userId == user.id,
        s10nsListService.createSubscriptionMessage(user, subscription, data.page),
        Errors.accessDenied
      )
        .sequence

    val tgUser = cb.from.toUser()
    for {
      user <- userRepo.getOrCreate(tgUser).transact(xa)
      s10nOpt <- s10nRepo.getById(data.subscriptionId).transact(xa)
      replyOpt <- s10nOpt.traverse(checkUserAndGetMessage(user, _))
    } yield replyOpt.toRight[String](Errors.notFound).flatten
  }

  def onListCommand(from: User, page: PageNumber): F[ReplyMessage] =
    s10nRepo.getByUserId(from.id)
      .transact(xa)
      .flatMap {
        s10nsListService.createSubscriptionsPage(_, page, from.defaultCurrency)
      }

  def onEditS10nCb(cb: CallbackQuery, data: EditS10nCbData): F[Either[String, InlineKeyboardMarkup]] = {
    def checkUserAndGetMarkup(subscription: Subscription) =
      Either.cond(
        subscription.userId == UserId(cb.from.id.toLong),
        s10nsListService.createEditS10nMarkup(subscription, data.page),
        Errors.accessDenied
      )

    s10nRepo.getById(data.subscriptionId)
      .transact(xa)
      .map {
        _.map(checkUserAndGetMarkup)
          .toRight[String](Errors.notFound)
          .flatten
      }
  }
}
