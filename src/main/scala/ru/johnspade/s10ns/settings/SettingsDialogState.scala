package ru.johnspade.s10ns.settings

import enumeratum._
import ru.johnspade.s10ns.bot.engine.{DialogState, StateEvent}

import scala.collection.immutable.IndexedSeq

sealed abstract class SettingsDialogState(override val message: String) extends EnumEntry with DialogState

object SettingsDialogState
  extends Enum[SettingsDialogState]
    with CirceEnum[SettingsDialogState] {
  case object DefaultCurrency extends SettingsDialogState("Default currency:")
  case object Finished extends SettingsDialogState("Default currency set.")

  override val values: IndexedSeq[SettingsDialogState] = findValues

  def transition(state: SettingsDialogState, event: SettingsDialogEvent): SettingsDialogState = {
    import SettingsDialogEvent._

    val e = event
    state match {
      case DefaultCurrency =>
        e match {
          case ChosenDefaultCurrency => Finished
          case _ => state
        }
      case _ => state
    }
  }
}

sealed trait SettingsDialogEvent extends EnumEntry with StateEvent

object SettingsDialogEvent extends Enum[SettingsDialogEvent] with CirceEnum[SettingsDialogEvent] {
  case object ChosenDefaultCurrency extends SettingsDialogEvent
  override def values: IndexedSeq[SettingsDialogEvent] = findValues
}
