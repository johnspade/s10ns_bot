package ru.johnspade.s10ns.subscription

import java.time.temporal.ChronoUnit
import scala.collection.immutable.IndexedSeq

import com.ibm.icu.util.MeasureUnit
import enumeratum._

sealed abstract class BillingPeriodUnit(val chronoUnit: ChronoUnit, val measureUnit: MeasureUnit)
  extends EnumEntry

object BillingPeriodUnit
  extends Enum[BillingPeriodUnit] with CirceEnum[BillingPeriodUnit] with DoobieEnum[BillingPeriodUnit] {
  case object Day extends BillingPeriodUnit(ChronoUnit.DAYS, MeasureUnit.DAY)
  case object Week extends BillingPeriodUnit(ChronoUnit.WEEKS, MeasureUnit.WEEK)
  case object Month extends BillingPeriodUnit(ChronoUnit.MONTHS, MeasureUnit.MONTH)
  case object Year extends BillingPeriodUnit(ChronoUnit.YEARS, MeasureUnit.YEAR)

  override val values: IndexedSeq[BillingPeriodUnit] = findValues
}
