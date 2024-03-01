package ru.johnspade.s10ns.notifications

import java.time.Instant
import java.time.temporal.ChronoUnit

import cats.Monad
import cats.instances.option._
import cats.syntax.functor._
import cats.syntax.traverse._

import ru.johnspade.s10ns.subscription.Subscription
import ru.johnspade.s10ns.subscription.service.S10nInfoService

class NotificationService[F[_]: Monad](
    private val hoursBefore: Int,
    private val s10nInfoService: S10nInfoService[F]
) {
  def needNotification(s10n: Subscription, cutoff: Instant): F[Boolean] =
    s10n.firstPaymentDate
      .traverse {
        s10nInfoService
          .getNextPaymentTimestamp(_, s10n.billingPeriod)
          .map(lessThanHoursBefore(cutoff, _) && s10n.sendNotifications && !isNotified(s10n, cutoff))
      }
      .map(_.getOrElse(false))

  def isNotified(s10n: Subscription, cutoff: Instant): Boolean =
    s10n.lastNotification.exists(lessThanHoursBefore(_, cutoff, hoursBefore + 1))

  private def lessThanHoursBefore(instant1: Instant, instant2: Instant, hours: Int = hoursBefore): Boolean =
    0 until hours contains ChronoUnit.HOURS.between(instant1, instant2)
}
