package ru.johnspade.s10ns.subscription.dialog

import scala.collection.immutable.IndexedSeq

import cats.syntax.option._

import enumeratum._
import telegramium.bots.KeyboardMarkup

import ru.johnspade.s10ns.bot.Markup
import ru.johnspade.s10ns.bot.Messages
import ru.johnspade.s10ns.bot.engine.DialogState
import ru.johnspade.s10ns.bot.engine.StateEvent

sealed abstract class EditS10nCurrencyDialogState(
    override val message: String,
    override val markup: Option[KeyboardMarkup]
) extends EnumEntry
    with DialogState

object EditS10nCurrencyDialogState
    extends Enum[EditS10nCurrencyDialogState]
    with CirceEnum[EditS10nCurrencyDialogState] {
  case object Currency extends EditS10nCurrencyDialogState(Messages.Currency, Markup.CurrencyReplyMarkup.some)
  case object Amount   extends EditS10nCurrencyDialogState(Messages.Amount, None)
  case object Finished extends EditS10nCurrencyDialogState(Messages.S10nSaved, None)

  override def values: IndexedSeq[EditS10nCurrencyDialogState] = findValues

  def transition(
      state: EditS10nCurrencyDialogState,
      event: EditS10nCurrencyDialogEvent
  ): EditS10nCurrencyDialogState = {
    import EditS10nCurrencyDialogEvent._

    val e = event
    state match {
      case Currency =>
        e match {
          case ChosenCurrency => Amount
          case _              => state
        }
      case Amount =>
        e match {
          case EnteredAmount => Finished
          case _             => state
        }
      case _ => state
    }
  }
}

sealed trait EditS10nCurrencyDialogEvent extends EnumEntry with StateEvent

object EditS10nCurrencyDialogEvent
    extends Enum[EditS10nCurrencyDialogEvent]
    with CirceEnum[EditS10nCurrencyDialogEvent] {
  case object ChosenCurrency extends EditS10nCurrencyDialogEvent
  case object EnteredAmount  extends EditS10nCurrencyDialogEvent

  override def values: IndexedSeq[EditS10nCurrencyDialogEvent] = findValues
}
