package ru.johnspade.s10ns.subscription.service

import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneOffset
import scala.jdk.CollectionConverters._

import cats.Monad
import cats.effect.Clock
import cats.implicits._

import org.joda.money.Money

import ru.johnspade.s10ns.currentTimestamp
import ru.johnspade.s10ns.subscription.BillingPeriod
import ru.johnspade.s10ns.subscription.RemainingTime
import ru.johnspade.s10ns.subscription.tags.FirstPaymentDate

class S10nInfoService[F[_]: Monad: Clock] {
  def getNextPaymentDate(start: FirstPaymentDate, billingPeriod: Option[BillingPeriod]): F[LocalDate] =
    currentTimestamp.map { now =>
      val today = now.atZone(ZoneOffset.UTC).toLocalDate
      billingPeriod.map { period =>
        val periodsPassed = calcPeriodsPassed(today, start, period)
        start.plus(periodsPassed * period.duration, period.unit.chronoUnit)
      }
        .getOrElse(start)
    }

  def getNextPaymentTimestamp(start: FirstPaymentDate, billingPeriod: Option[BillingPeriod]): F[Instant] =
    getNextPaymentDate(start, billingPeriod)
      .map(_.atStartOfDay(ZoneOffset.UTC).toInstant)

  def getRemainingTime(nextPaymentDate: LocalDate): F[Option[RemainingTime]] =
    currentTimestamp.map { now =>
      val today = now.atZone(ZoneOffset.UTC).toLocalDate
      val between = Period.between(today, nextPaymentDate)
      between.getUnits
        .asScala
        .map(unit => (unit, between.get(unit)))
        .find(_._2 != 0)
        .map {
          case (unit, count) => RemainingTime(unit, count)
        }
    }

  def getPaidInTotal(amount: Money, start: FirstPaymentDate, billingPeriod: BillingPeriod): F[Money] =
    calculatePeriodsPassed(start, billingPeriod).map(amount.multipliedBy)

  private def calculatePeriodsPassed(start: FirstPaymentDate, billingPeriod: BillingPeriod) =
    currentTimestamp.map { now =>
      val today = now.atZone(ZoneOffset.UTC).toLocalDate
      calcPeriodsPassed(today, start, billingPeriod)
    }

  private def calcPeriodsPassed(today: LocalDate, start: FirstPaymentDate, billingPeriod: BillingPeriod) = {
    import billingPeriod.duration
    import billingPeriod.unit.chronoUnit

    val unitsPassed = chronoUnit.between(start, Set(today.plus(duration, chronoUnit).minusDays(1), start).max)
    unitsPassed / duration
  }
}
