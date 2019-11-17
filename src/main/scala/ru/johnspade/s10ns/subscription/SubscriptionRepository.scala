package ru.johnspade.s10ns.subscription

import doobie.free.connection.ConnectionIO
import ru.johnspade.s10ns.user.UserId

trait SubscriptionRepository {
  def create(draft: SubscriptionDraft): ConnectionIO[Subscription]

  def getById(id: SubscriptionId): ConnectionIO[Option[Subscription]]

  def getByUserId(userId: UserId): ConnectionIO[List[Subscription]]

  def remove(id: SubscriptionId): ConnectionIO[Unit]
}
