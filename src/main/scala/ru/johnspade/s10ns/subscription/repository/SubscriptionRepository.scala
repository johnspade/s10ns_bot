package ru.johnspade.s10ns.subscription.repository

import doobie.free.connection.ConnectionIO
import ru.johnspade.s10ns.subscription.tags._
import ru.johnspade.s10ns.subscription.{Subscription, SubscriptionDraft}
import ru.johnspade.s10ns.user.tags._

trait SubscriptionRepository {
  def create(draft: SubscriptionDraft): ConnectionIO[Subscription]

  def getById(id: SubscriptionId): ConnectionIO[Option[Subscription]]

  def getByUserId(userId: UserId): ConnectionIO[List[Subscription]]

  def remove(id: SubscriptionId): ConnectionIO[Unit]

  def update(s10n: Subscription): ConnectionIO[Option[Subscription]]
}