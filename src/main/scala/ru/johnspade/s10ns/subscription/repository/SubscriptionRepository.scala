package ru.johnspade.s10ns.subscription.repository

import ru.johnspade.s10ns.subscription.tags._
import ru.johnspade.s10ns.subscription.{Subscription, SubscriptionDraft}
import ru.johnspade.s10ns.user.tags._

trait SubscriptionRepository[D[_]] {
  def create(draft: SubscriptionDraft): D[Subscription]

  def getById(id: SubscriptionId): D[Option[Subscription]]

  def getByUserId(userId: UserId): D[List[Subscription]]

  def remove(id: SubscriptionId): D[Unit]

  def update(s10n: Subscription): D[Option[Subscription]]
}
