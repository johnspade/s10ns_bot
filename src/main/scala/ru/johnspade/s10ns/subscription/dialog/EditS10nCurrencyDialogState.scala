package ru.johnspade.s10ns.subscription.dialog

import enumeratum._
import ru.johnspade.s10ns.bot.Messages
import ru.johnspade.s10ns.bot.engine.{DialogState, StateEvent}

import scala.collection.immutable.IndexedSeq

sealed abstract class EditS10nCurrencyDialogState(override val message: String)
  extends EnumEntry with DialogState

object EditS10nCurrencyDialogState extends Enum[EditS10nCurrencyDialogState] with CirceEnum[EditS10nCurrencyDialogState] {
  case object Currency extends EditS10nCurrencyDialogState(Messages.Currency)
  case object Amount extends EditS10nCurrencyDialogState(Messages.Amount)
  case object Finished extends EditS10nCurrencyDialogState(Messages.S10nSaved)

  override def values: IndexedSeq[EditS10nCurrencyDialogState] = findValues

  def transition(state: EditS10nCurrencyDialogState, event: EditS10nCurrencyDialogEvent): EditS10nCurrencyDialogState = {
    import EditS10nCurrencyDialogEvent._

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

sealed trait EditS10nCurrencyDialogEvent extends EnumEntry with StateEvent

object EditS10nCurrencyDialogEvent extends Enum[EditS10nCurrencyDialogEvent] with CirceEnum[EditS10nCurrencyDialogEvent] {
  case object ChosenCurrency extends EditS10nCurrencyDialogEvent
  case object EnteredAmount extends EditS10nCurrencyDialogEvent

  override def values: IndexedSeq[EditS10nCurrencyDialogEvent] = findValues
}
