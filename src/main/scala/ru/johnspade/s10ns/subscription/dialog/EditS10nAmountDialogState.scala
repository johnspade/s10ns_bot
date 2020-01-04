package ru.johnspade.s10ns.subscription.dialog

import enumeratum._
import ru.johnspade.s10ns.bot.engine.{DialogState, StateEvent}

import scala.collection.immutable.IndexedSeq

sealed abstract class EditS10nAmountDialogState(override val message: String)
  extends EnumEntry with DialogState

object EditS10nAmountDialogState extends Enum[EditS10nAmountDialogState] with CirceEnum[EditS10nAmountDialogState] {
  case object Currency extends EditS10nAmountDialogState("Currency:")
  case object Amount extends EditS10nAmountDialogState("Amount:")
  case object Finished extends EditS10nAmountDialogState("Currency saved.")

  override def values: IndexedSeq[EditS10nAmountDialogState] = findValues

  def transition(state: EditS10nAmountDialogState, event: EditS10nAmountDialogEvent): EditS10nAmountDialogState = {
    import EditS10nAmountDialogEvent._

    val e = event
    state match {
      case Currency =>
        e match {
          case ChosenCurrency => Amount
          case _ => state
        }
      case Amount =>
        e match {
          case EnteredAmount => Finished
          case _ => state
        }
      case _ => state
    }
  }
}

sealed trait EditS10nAmountDialogEvent extends EnumEntry with StateEvent

object EditS10nAmountDialogEvent extends Enum[EditS10nAmountDialogEvent] with CirceEnum[EditS10nAmountDialogEvent] {
  case object ChosenCurrency extends EditS10nAmountDialogEvent
  case object EnteredAmount extends EditS10nAmountDialogEvent

  override def values: IndexedSeq[EditS10nAmountDialogEvent] = findValues
}
