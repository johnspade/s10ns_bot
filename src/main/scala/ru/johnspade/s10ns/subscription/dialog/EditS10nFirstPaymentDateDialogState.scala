package ru.johnspade.s10ns.subscription.dialog

import enumeratum._
import ru.johnspade.s10ns.bot.Messages
import ru.johnspade.s10ns.bot.engine.{DialogState, StateEvent}
import telegramium.bots.KeyboardMarkup

import scala.collection.immutable.IndexedSeq

sealed abstract class EditS10nFirstPaymentDateDialogState(
  override val message: String,
  override val markup: Option[KeyboardMarkup]
)
  extends EnumEntry with DialogState

object EditS10nFirstPaymentDateDialogState
  extends Enum[EditS10nFirstPaymentDateDialogState] with CirceEnum[EditS10nFirstPaymentDateDialogState] {
  case object FirstPaymentDate extends EditS10nFirstPaymentDateDialogState(Messages.FirstPaymentDate, None)
  case object Finished extends EditS10nFirstPaymentDateDialogState(Messages.S10nSaved, None)
  override def values: IndexedSeq[EditS10nFirstPaymentDateDialogState] = findValues

  def transition(
    state: EditS10nFirstPaymentDateDialogState,
    event: EditS10nFirstPaymentDateDialogEvent
  ): EditS10nFirstPaymentDateDialogState = {
    import EditS10nFirstPaymentDateDialogEvent._

    val e = event
    state match {
      case FirstPaymentDate =>
        e match {
          case ChosenFirstPaymentDate | RemovedFirstPaymentDate => Finished
          case _ => state
        }
      case _ => state
    }
  }
}

sealed trait EditS10nFirstPaymentDateDialogEvent extends EnumEntry with StateEvent

object EditS10nFirstPaymentDateDialogEvent
  extends Enum[EditS10nFirstPaymentDateDialogEvent] with CirceEnum[EditS10nFirstPaymentDateDialogEvent] {
  case object RemovedFirstPaymentDate extends EditS10nFirstPaymentDateDialogEvent
  case object ChosenFirstPaymentDate extends EditS10nFirstPaymentDateDialogEvent

  override def values: IndexedSeq[EditS10nFirstPaymentDateDialogEvent] = findValues
}
