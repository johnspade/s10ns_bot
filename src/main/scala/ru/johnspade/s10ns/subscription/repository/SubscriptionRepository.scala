package ru.johnspade.s10ns.subscription.repository

import java.time.Instant

import ru.johnspade.s10ns.subscription.Subscription
import ru.johnspade.s10ns.subscription.SubscriptionDraft
import ru.johnspade.s10ns.user.User

trait SubscriptionRepository[D[_]] {
  def create(draft: SubscriptionDraft): D[Subscription]

  def getById(id: Long): D[Option[Subscription]]

  def getByIdWithUser(id: Long): D[Option[(Subscription, User)]]

  def getByUserId(userId: Long): D[List[Subscription]]

  def collectNotifiable(cutoff: Instant): D[List[Subscription]]

  def remove(id: Long): D[Unit]

  def update(s10n: Subscription): D[Option[Subscription]]

  def disableNotificationsForUser(userId: Long): D[Unit]
}
