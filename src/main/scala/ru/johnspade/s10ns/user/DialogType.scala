package ru.johnspade.s10ns.user

import enumeratum._

import scala.collection.immutable.IndexedSeq

sealed abstract class DialogType extends EnumEntry

object DialogType extends Enum[DialogType] with DoobieEnum [DialogType] {
  case object CreateSubscription extends DialogType
  case object UpdateSubscription extends DialogType
  case object Settings extends DialogType

  override val values: IndexedSeq[DialogType] = findValues
}
