package ru.johnspade.s10ns

import java.time.Instant
import java.time.temporal.ChronoUnit

import ru.johnspade.s10ns.subscription.Subscription

package object notifications {
  def lessThan23Hours(instant1: Instant, instant2: Instant): Boolean =
    0 until 23 contains ChronoUnit.HOURS.between(instant1, instant2)

  def isNotified(s10n: Subscription, now: Instant): Boolean =
    s10n.lastNotification.exists(lessThan23Hours(_, now))
}
