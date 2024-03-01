package ru.johnspade.s10ns.notifications

import io.chrisdavenport.fuuid.FUUID

import ru.johnspade.s10ns.subscription.tags.SubscriptionId

case class Notification(id: FUUID, subscriptionId: SubscriptionId, retriesRemaining: Int = 3)
