package ru.johnspade.s10ns.user

import enumeratum._

import scala.collection.immutable.IndexedSeq

sealed abstract class EditS10nOneTimeDialogState(override val message: String)
  extends EnumEntry with EditS10nDialogState

object EditS10nOneTimeDialogState extends Enum[EditS10nOneTimeDialogState] with CirceEnum[EditS10nOneTimeDialogState] {
  case object IsOneTime extends EditS10nOneTimeDialogState("Recurring/one time:")
  case object BillingPeriodUnit extends EditS10nOneTimeDialogState("Billing period unit:")
  case object BillingPeriodDuration extends EditS10nOneTimeDialogState("Billing period duration:")
  case object Finished extends EditS10nOneTimeDialogState("Saved.")

  override def values: IndexedSeq[EditS10nOneTimeDialogState] = findValues

  def transition(state: EditS10nOneTimeDialogState, event: EditS10nOneTimeDialogEvent): EditS10nOneTimeDialogState = {
    import EditS10nOneTimeDialogEvent._

    val e = event
    state match {
      case IsOneTime =>
        e match {
          case ChosenOneTime | ChosenRecurringWithPeriod => Finished
          case ChosenRecurringWithoutPeriod => BillingPeriodUnit
          case _ => state
        }
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

sealed trait EditS10nOneTimeDialogEvent extends EnumEntry

object EditS10nOneTimeDialogEvent extends Enum[EditS10nOneTimeDialogEvent] {
  case object ChosenOneTime extends EditS10nOneTimeDialogEvent
  case object ChosenRecurringWithPeriod extends EditS10nOneTimeDialogEvent
  case object ChosenRecurringWithoutPeriod extends EditS10nOneTimeDialogEvent
  case object ChosenBillingPeriodUnit extends EditS10nOneTimeDialogEvent
  case object ChosenBillingPeriodDuration extends EditS10nOneTimeDialogEvent

  override def values: IndexedSeq[EditS10nOneTimeDialogEvent] = findValues
}
