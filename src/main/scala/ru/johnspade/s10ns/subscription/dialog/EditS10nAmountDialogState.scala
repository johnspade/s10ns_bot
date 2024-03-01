package ru.johnspade.s10ns.subscription.dialog

import scala.collection.immutable.IndexedSeq

import enumeratum._
import telegramium.bots.KeyboardMarkup

import ru.johnspade.s10ns.bot.Messages
import ru.johnspade.s10ns.bot.engine.DialogState
import ru.johnspade.s10ns.bot.engine.StateEvent

sealed abstract class EditS10nAmountDialogState(
    override val message: String,
    override val markup: Option[KeyboardMarkup]
) extends EnumEntry
    with DialogState

object EditS10nAmountDialogState extends Enum[EditS10nAmountDialogState] with CirceEnum[EditS10nAmountDialogState] {
  case object Amount   extends EditS10nAmountDialogState(Messages.Amount, None)
  case object Finished extends EditS10nAmountDialogState(Messages.S10nSaved, None)

  override def values: IndexedSeq[EditS10nAmountDialogState] = findValues

  def transition(state: EditS10nAmountDialogState, event: EditS10nAmountDialogEvent): EditS10nAmountDialogState = {
    import EditS10nAmountDialogEvent._

    val e = event
    state match {
      case Amount =>
        e match {
          case EnteredAmount => Finished
          case _             => state
        }
      case _ => state
    }
  }
}

sealed trait EditS10nAmountDialogEvent extends EnumEntry with StateEvent

object EditS10nAmountDialogEvent extends Enum[EditS10nAmountDialogEvent] with CirceEnum[EditS10nAmountDialogEvent] {
  case object EnteredAmount extends EditS10nAmountDialogEvent

  override def values: IndexedSeq[EditS10nAmountDialogEvent] = findValues
}
