package ru.johnspade.s10ns.subscription.repository

import java.time.Instant
import java.time.LocalDate
import scala.collection.concurrent.TrieMap

import cats.Id
import cats.syntax.option._

import ru.johnspade.s10ns.subscription.Subscription
import ru.johnspade.s10ns.subscription.SubscriptionDraft
import ru.johnspade.s10ns.subscription.tags.SubscriptionId
import ru.johnspade.s10ns.user.User
import ru.johnspade.s10ns.user.tags.UserId

class InMemorySubscriptionRepository extends SubscriptionRepository[Id] {
  val subscriptions: TrieMap[SubscriptionId, Subscription] = TrieMap.empty
  private var id: Long = 0

  override def create(draft: SubscriptionDraft): Subscription = {
    val s10n = Subscription.fromDraft(draft, SubscriptionId(id))
    id += 1
    subscriptions.put(s10n.id, s10n)
    s10n
  }

  override def getById(id: SubscriptionId): Option[Subscription] = subscriptions.get(id)

  def getByIdWithUser(id: SubscriptionId): Id[Option[(Subscription, User)]] = ???

  override def getByUserId(userId: UserId): List[Subscription] = subscriptions.values.filter(_.userId == userId).toList

  def collectNotifiable(cutoff: Instant): Id[List[Subscription]] =
    subscriptions
      .values
      .filter { s =>
        s.sendNotifications &&
          (s.firstPaymentDate.isDefined && s.billingPeriod.isDefined || s.firstPaymentDate.exists(_.isAfter(LocalDate.now)))
      }
      .toList

  override def remove(id: SubscriptionId): Unit = subscriptions.remove(id)

  override def update(s10n: Subscription): Option[Subscription] = {
    subscriptions.put(s10n.id, s10n)
    s10n.some
  }

  override def disableNotificationsForUser(userId: UserId): Id[Unit] =
    subscriptions
      .values
      .filter(_.userId == userId)
      .map(_.copy(sendNotifications = false))
      .foreach { s =>
        subscriptions.put(s.id, s)
      }
}
