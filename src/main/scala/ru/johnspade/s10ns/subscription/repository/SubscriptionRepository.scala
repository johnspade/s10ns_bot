package ru.johnspade.s10ns.subscription.repository

import java.time.Instant

import ru.johnspade.s10ns.subscription.tags._
import ru.johnspade.s10ns.subscription.{Subscription, SubscriptionDraft}
import ru.johnspade.s10ns.user.User
import ru.johnspade.s10ns.user.tags._

trait SubscriptionRepository[D[_]] {
  def create(draft: SubscriptionDraft): D[Subscription]

  def getById(id: SubscriptionId): D[Option[Subscription]]

  def getByIdWithUser(id: SubscriptionId): D[Option[(Subscription, User)]]

  def getByUserId(userId: UserId): D[List[Subscription]]

  def collectNotifiable(cutoff: Instant): D[List[Subscription]]

  def remove(id: SubscriptionId): D[Unit]

  def update(s10n: Subscription): D[Option[Subscription]]
}
