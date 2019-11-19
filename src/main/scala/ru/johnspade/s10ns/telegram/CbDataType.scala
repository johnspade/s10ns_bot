package ru.johnspade.s10ns.telegram

import enumeratum.values.{IntEnum, IntEnumEntry}

import scala.collection.immutable.IndexedSeq

sealed abstract class CbDataType(val value: Int) extends IntEnumEntry {
  def hex: String = value.toHexString
}

object CbDataType extends IntEnum[CbDataType] {
  case object Subscriptions extends CbDataType(0x0)
  case object Subscription extends CbDataType(0x1)
  case object BillingPeriodUnit extends CbDataType(0x2)
  case object OneTime extends CbDataType(0x3)
  case object Ignore extends CbDataType(0x4)
  case object Calendar extends CbDataType(0x5)
  case object FirstPaymentDate extends CbDataType(0x6)
  case object DefaultCurrency extends CbDataType(0x7)
  case object RemoveSubscription extends CbDataType(0x8)
  case object EditS10n extends CbDataType(0x9)
  case object EditS10nName extends CbDataType(0xA)

  override def values: IndexedSeq[CbDataType] = findValues
}
