package ru.johnspade.s10ns.notifications

import java.util.UUID

case class Notification(id: UUID, subscriptionId: Long, retriesRemaining: Int = 3)
