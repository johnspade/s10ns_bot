package ru.johnspade.s10ns.notifications

import java.util.UUID

import ru.johnspade.s10ns.subscription.tags.SubscriptionId

case class Notification(id: UUID, subscriptionId: SubscriptionId, retriesRemaining: Int = 3)
