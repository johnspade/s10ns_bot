package ru.johnspade.s10ns.subscription.repository

import cats.Id
import cats.syntax.option._
import ru.johnspade.s10ns.subscription.{Subscription, SubscriptionDraft}
import ru.johnspade.s10ns.subscription.tags.SubscriptionId
import ru.johnspade.s10ns.user.tags.UserId

import scala.collection.concurrent.TrieMap

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
  
  override def getByUserId(userId: UserId): List[Subscription] = subscriptions.values.filter(_.userId == userId).toList

  override def remove(id: SubscriptionId): Unit = subscriptions.remove(id)

  override def update(s10n: Subscription): Option[Subscription] = {
    subscriptions.put(s10n.id, s10n)
    s10n.some
  }
}
