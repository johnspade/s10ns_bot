package ru.johnspade.s10ns.subscription.dialog

import enumeratum._
import ru.johnspade.s10ns.bot.Messages
import ru.johnspade.s10ns.bot.engine.{DialogState, StateEvent}

import scala.collection.immutable.IndexedSeq

sealed abstract class EditS10nBillingPeriodDialogState(override val message: String)
  extends EnumEntry with DialogState

object EditS10nBillingPeriodDialogState
  extends Enum[EditS10nBillingPeriodDialogState] with CirceEnum[EditS10nBillingPeriodDialogState] {
  case object BillingPeriodUnit extends EditS10nBillingPeriodDialogState(Messages.BillingPeriodUnit)
  case object BillingPeriodDuration extends EditS10nBillingPeriodDialogState(Messages.BillingPeriodDuration)
  case object Finished extends EditS10nBillingPeriodDialogState("Saved.")

  override def values: IndexedSeq[EditS10nBillingPeriodDialogState] = findValues

  def transition(state: EditS10nBillingPeriodDialogState, event: EditS10nBillingPeriodEvent): EditS10nBillingPeriodDialogState = {
    import EditS10nBillingPeriodEvent._

    val e = event
    state match {
      case BillingPeriodUnit =>
        e match {
          case ChosenBillingPeriodUnit => BillingPeriodDuration
          case _ => state
        }
      case BillingPeriodDuration =>
        e match {
          case ChosenBillingPeriodDuration => Finished
          case _ => state
        }
      case _ => state
    }
  }
}

sealed trait EditS10nBillingPeriodEvent extends EnumEntry with StateEvent

object EditS10nBillingPeriodEvent extends Enum[EditS10nBillingPeriodEvent] with CirceEnum[EditS10nBillingPeriodEvent] {
  case object ChosenBillingPeriodUnit extends EditS10nBillingPeriodEvent
  case object ChosenBillingPeriodDuration extends EditS10nBillingPeriodEvent

  override def values: IndexedSeq[EditS10nBillingPeriodEvent] = findValues
}
