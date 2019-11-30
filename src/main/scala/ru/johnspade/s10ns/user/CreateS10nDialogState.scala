package ru.johnspade.s10ns.user

import enumeratum._

import scala.collection.immutable.IndexedSeq

sealed abstract class CreateS10nDialogState(val message: String) extends EnumEntry

object CreateS10nDialogState
  extends Enum[CreateS10nDialogState]
    with CirceEnum[CreateS10nDialogState] {
  case object Name extends CreateS10nDialogState("Name:")
  case object Currency extends CreateS10nDialogState("Currency:")
  case object Amount extends CreateS10nDialogState("Amount:")
  case object IsOneTime extends CreateS10nDialogState("Recurring/one time:")
  case object BillingPeriodUnit extends CreateS10nDialogState("Billing period unit:")
  case object BillingPeriodDuration extends CreateS10nDialogState("Billing period duration:")
  case object FirstPaymentDate extends CreateS10nDialogState("First payment date:")
  case object Finished extends CreateS10nDialogState("Subscription created.")

  override val values: IndexedSeq[CreateS10nDialogState] = findValues

  def transition(state: CreateS10nDialogState, event: CreateS10nDialogEvent): CreateS10nDialogState = {
    import ru.johnspade.s10ns.user.CreateS10nDialogEvent._

    val e = event
    state match {
      case Currency =>
        e match {
          case ChosenCurrency => Name
          case _ => state
        }
      case Name =>
        e match {
          case EnteredName => Amount
          case _ => state
        }
      case Amount =>
        e match {
          case EnteredAmount => IsOneTime
          case _ => state
        }
      case IsOneTime =>
        e match {
          case ChosenOneTime => FirstPaymentDate
          case ChosenRecurring => BillingPeriodUnit
          case _ => state
        }
      case BillingPeriodUnit =>
        e match {
          case ChosenBillingPeriodUnit => BillingPeriodDuration
          case _ => state
        }
      case BillingPeriodDuration =>
        e match {
          case EnteredBillingPeriodDuration => FirstPaymentDate
          case _ => state
        }
      case FirstPaymentDate =>
        e match {
          case ChosenFirstPaymentDate => Finished
          case _ => state
        }
      case _ => state
    }
  }
}

sealed trait CreateS10nDialogEvent extends EnumEntry

object CreateS10nDialogEvent extends Enum[CreateS10nDialogEvent] {
  case object EnteredName extends CreateS10nDialogEvent
  case object ChosenCurrency extends CreateS10nDialogEvent
  case object EnteredAmount extends CreateS10nDialogEvent
  case object ChosenOneTime extends CreateS10nDialogEvent
  case object ChosenRecurring extends CreateS10nDialogEvent
  case object EnteredBillingPeriodDuration extends CreateS10nDialogEvent
  case object ChosenBillingPeriodUnit extends CreateS10nDialogEvent
  case object ChosenFirstPaymentDate extends CreateS10nDialogEvent

  override def values: IndexedSeq[CreateS10nDialogEvent] = findValues
}
