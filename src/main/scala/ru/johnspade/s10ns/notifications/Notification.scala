package ru.johnspade.s10ns.notifications

import java.time.Instant
import java.util.UUID

import ru.johnspade.s10ns.subscription.tags.SubscriptionId

case class Notification(
  id: UUID,
  timestamp: Instant,
  retriesRemaining: Int,
  subscriptionId: SubscriptionId
)
