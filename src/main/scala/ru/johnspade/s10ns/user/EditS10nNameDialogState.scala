package ru.johnspade.s10ns.user

import enumeratum._

import scala.collection.immutable.IndexedSeq

sealed abstract class EditS10nNameDialogState(override val message: String)
  extends EnumEntry with StateWithMessage

object EditS10nNameDialogState
  extends Enum[EditS10nNameDialogState]
    with CirceEnum[EditS10nNameDialogState] {
  case object Name extends EditS10nNameDialogState("Name:")
  case object Finished extends EditS10nNameDialogState("Name saved.")

  override def values: IndexedSeq[EditS10nNameDialogState] = findValues

  def transition(state: EditS10nNameDialogState, event: EditS10nNameDialogEvent): EditS10nNameDialogState = {
    import EditS10nNameDialogEvent._

    val e = event
    state match {
      case Name =>
        e match {
          case EnteredName => Finished
          case _ => state
        }
      case _ => state
    }
  }
}

sealed trait EditS10nNameDialogEvent extends EnumEntry

object EditS10nNameDialogEvent extends Enum[EditS10nNameDialogEvent] {
  case object EnteredName extends EditS10nNameDialogEvent

  override def values: IndexedSeq[EditS10nNameDialogEvent] = findValues
}
