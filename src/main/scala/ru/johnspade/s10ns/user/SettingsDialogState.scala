package ru.johnspade.s10ns.user

import enumeratum._

import scala.collection.immutable.IndexedSeq

sealed abstract class SettingsDialogState extends EnumEntry

object SettingsDialogState extends Enum[SettingsDialogState] with DoobieEnum[SettingsDialogState] {
  case object DefaultCurrency extends SettingsDialogState

  override val values: IndexedSeq[SettingsDialogState] = findValues
}
