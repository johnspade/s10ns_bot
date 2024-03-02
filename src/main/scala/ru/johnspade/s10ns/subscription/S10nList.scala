package ru.johnspade.s10ns.subscription

import java.time.temporal.TemporalUnit

import org.joda.money.Money

case class RemainingTime(unit: TemporalUnit, count: Long)

sealed abstract class S10nAmount {
  def amount: Money
}

final case class ExactAmount(override val amount: Money)    extends S10nAmount
final case class NonExactAmount(override val amount: Money) extends S10nAmount

final case class S10nItem(
    index: Int,
    id: Long,
    name: String,
    amount: S10nAmount,
    remainingTime: Option[RemainingTime]
)

final case class S10nList(
    sum: Money,
    period: BillingPeriodUnit,
    items: List[S10nItem],
    nextPeriod: BillingPeriodUnit,
    page: Int,
    totalSize: Int
)
