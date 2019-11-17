package ru.johnspade.s10ns.telegram

import enumeratum.values.{IntEnum, IntEnumEntry}

import scala.collection.immutable.IndexedSeq

sealed abstract class CallbackDataType(val value: Int) extends IntEnumEntry {
  def hex: String = value.toHexString
}

object CallbackDataType extends IntEnum[CallbackDataType] {
  case object Subscriptions extends CallbackDataType(0x0)
  case object Subscription extends CallbackDataType(0x1)
  case object BillingPeriodUnit extends CallbackDataType(0x2)
  case object OneTime extends CallbackDataType(0x3)
  case object Ignore extends CallbackDataType(0x4)
  case object Calendar extends CallbackDataType(0x5)
  case object FirstPaymentDate extends CallbackDataType(0x6)
  case object DefaultCurrency extends CallbackDataType(0x7)
  case object RemoveSubscription extends CallbackDataType(0x8)

  override def values: IndexedSeq[CallbackDataType] = findValues
}
