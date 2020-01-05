package ru.johnspade.s10ns.subscription.dialog

import enumeratum._
import ru.johnspade.s10ns.bot.Messages
import ru.johnspade.s10ns.bot.engine.{DialogState, StateEvent}

import scala.collection.immutable.IndexedSeq

sealed abstract class CreateS10nDialogState(override val message: String) extends EnumEntry with DialogState

object CreateS10nDialogState
  extends Enum[CreateS10nDialogState]
    with CirceEnum[CreateS10nDialogState] {
  case object Name extends CreateS10nDialogState(Messages.Name)
  case object Currency extends CreateS10nDialogState(Messages.Currency)
  case object Amount extends CreateS10nDialogState(Messages.Amount)
  case object IsOneTime extends CreateS10nDialogState(Messages.IsOneTime)
  case object BillingPeriodUnit extends CreateS10nDialogState(Messages.BillingPeriodUnit)
  case object BillingPeriodDuration extends CreateS10nDialogState(Messages.BillingPeriodDuration)
  case object FirstPaymentDate extends CreateS10nDialogState(Messages.FirstPaymentDate)
  case object Finished extends CreateS10nDialogState(Messages.S10nSaved)

  override val values: IndexedSeq[CreateS10nDialogState] = findValues

  def transition(state: CreateS10nDialogState, event: CreateS10nDialogEvent): CreateS10nDialogState = {
    import CreateS10nDialogEvent._

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
          case SkippedIsOneTime => Finished
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
          case ChosenFirstPaymentDate | SkippedFirstPaymentDate => Finished
          case _ => state
        }
      case _ => state
    }
  }
}

sealed trait CreateS10nDialogEvent extends EnumEntry with StateEvent

object CreateS10nDialogEvent extends Enum[CreateS10nDialogEvent] with CirceEnum[CreateS10nDialogEvent] {
  case object EnteredName extends CreateS10nDialogEvent
  case object ChosenCurrency extends CreateS10nDialogEvent
  case object EnteredAmount extends CreateS10nDialogEvent
  case object SkippedIsOneTime extends CreateS10nDialogEvent
  case object ChosenOneTime extends CreateS10nDialogEvent
  case object ChosenRecurring extends CreateS10nDialogEvent
  case object EnteredBillingPeriodDuration extends CreateS10nDialogEvent
  case object ChosenBillingPeriodUnit extends CreateS10nDialogEvent
  case object SkippedFirstPaymentDate extends CreateS10nDialogEvent
  case object ChosenFirstPaymentDate extends CreateS10nDialogEvent

  override def values: IndexedSeq[CreateS10nDialogEvent] = findValues
}
