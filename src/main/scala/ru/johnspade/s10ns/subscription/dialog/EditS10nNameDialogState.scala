package ru.johnspade.s10ns.subscription.dialog

import enumeratum._
import ru.johnspade.s10ns.bot.Messages
import ru.johnspade.s10ns.bot.engine.{DialogState, StateEvent}
import telegramium.bots.KeyboardMarkup

import scala.collection.immutable.IndexedSeq

sealed abstract class EditS10nNameDialogState(override val message: String, override val markup: Option[KeyboardMarkup])
  extends EnumEntry with DialogState

object EditS10nNameDialogState
  extends Enum[EditS10nNameDialogState]
    with CirceEnum[EditS10nNameDialogState] {
  case object Name extends EditS10nNameDialogState(Messages.Name, None)
  case object Finished extends EditS10nNameDialogState(Messages.S10nSaved, None)

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

sealed trait EditS10nNameDialogEvent extends EnumEntry with StateEvent

object EditS10nNameDialogEvent extends Enum[EditS10nNameDialogEvent] with CirceEnum[EditS10nNameDialogEvent] {
  case object EnteredName extends EditS10nNameDialogEvent

  override def values: IndexedSeq[EditS10nNameDialogEvent] = findValues
}
