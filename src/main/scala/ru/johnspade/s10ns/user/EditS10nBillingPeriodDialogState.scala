package ru.johnspade.s10ns.user

import enumeratum._

import scala.collection.immutable.IndexedSeq

sealed abstract class EditS10nBillingPeriodDialogState(override val message: String)
  extends EnumEntry with StateWithMessage

object EditS10nBillingPeriodDialogState
  extends Enum[EditS10nBillingPeriodDialogState] with CirceEnum[EditS10nBillingPeriodDialogState] {
  case object BillingPeriodUnit extends EditS10nBillingPeriodDialogState("Billing period unit:") // todo common messages
  case object BillingPeriodDuration extends EditS10nBillingPeriodDialogState("Billing period duration:")
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

sealed trait EditS10nBillingPeriodEvent extends EnumEntry

object EditS10nBillingPeriodEvent extends Enum[EditS10nBillingPeriodEvent] {
  case object ChosenBillingPeriodUnit extends EditS10nBillingPeriodEvent
  case object ChosenBillingPeriodDuration extends EditS10nBillingPeriodEvent

  override def values: IndexedSeq[EditS10nBillingPeriodEvent] = findValues
}

