package ru.johnspade.s10ns.subscription

import cats.effect.Sync
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import ru.johnspade.s10ns.common.{Errors, PageNumber}
import ru.johnspade.s10ns.telegram.TelegramOps.TelegramUserOps
import ru.johnspade.s10ns.telegram.{RemoveSubscriptionCallbackData, ReplyMessage, SubscriptionCallbackData, SubscriptionsCallbackData}
import ru.johnspade.s10ns.user.{User, UserId, UserRepository}
import telegramium.bots.CallbackQuery

class SubscriptionListService[F[_] : Sync](
  private val userRepo: UserRepository,
  private val s10nRepo: SubscriptionRepository,
  private val xa: Transactor[F],
  private val s10nsListService: S10nsListMessageService[F]
) {
  def onSubscriptionsCb(cb: CallbackQuery): F[Either[String, ReplyMessage]] = {
    val tgUser = cb.from.toUser()
    userRepo.getOrCreate(tgUser)
      .transact(xa)
      .flatMap { user =>
        handleCbData(cb, data => {
          val pageRequest = SubscriptionsCallbackData.fromString(data)
          s10nsListService.createSubscriptionsPage(user, pageRequest.page)
        })
      }
  }

  def onRemoveSubscriptionCb(cb: CallbackQuery): F[Either[String, ReplyMessage]] =
    handleCbData(cb, data => {
      val request = RemoveSubscriptionCallbackData.fromString(data)
      for {
        _ <- s10nRepo.remove(request.subscriptionId).transact(xa)
        user <- userRepo.getOrCreate(cb.from.toUser()).transact(xa)
        reply <- s10nsListService.createSubscriptionsPage(user, request.page)
      } yield reply
    })

  def onSubcriptionCb(cb: CallbackQuery): F[Either[String, ReplyMessage]] = {
    cb.data.traverse { data =>
      val subscriptionRequest = SubscriptionCallbackData.fromString(data)
      s10nsListService.createSubscriptionMessage(
        UserId(cb.from.id),
        subscriptionRequest.subscriptionId,
        subscriptionRequest.page
      )
    }
      .map(_.toRight(Errors.default).flatten)
  }

  def onListCommand(from: User, page: PageNumber): F[ReplyMessage] = s10nsListService.createSubscriptionsPage(from, page)

  private def handleCbData(cb: CallbackQuery, f: String => F[ReplyMessage]): F[Either[String, ReplyMessage]] =
    cb.data.traverse(f).map(_.toRight(Errors.default))
}
