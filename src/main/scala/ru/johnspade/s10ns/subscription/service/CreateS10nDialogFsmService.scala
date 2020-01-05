package ru.johnspade.s10ns.subscription.service

import cats.effect.Sync
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.chrisdavenport.log4cats.Logger
import org.joda.money.{CurrencyUnit, Money}
import ru.johnspade.s10ns.bot.engine.{DialogEngine, ReplyMessage}
import ru.johnspade.s10ns.bot.{CreateS10nDialog, StateMessageService}
import ru.johnspade.s10ns.subscription.dialog.{CreateS10nDialogEvent, CreateS10nDialogState}
import ru.johnspade.s10ns.subscription.repository.SubscriptionRepository
import ru.johnspade.s10ns.subscription.tags._
import ru.johnspade.s10ns.user.{User, UserRepository}

class CreateS10nDialogFsmService[F[_] : Sync : Logger](
  private val subscriptionRepo: SubscriptionRepository,
  private val userRepo: UserRepository,
  private val stateMessageService: StateMessageService[F],
  private val dialogEngine: DialogEngine[F],
  private val s10nsListMessageService: S10nsListMessageService[F]
)(private implicit val xa: Transactor[F]) {

  def saveName(user: User, dialog: CreateS10nDialog, name: SubscriptionName): F[List[ReplyMessage]] = {
    val draft = dialog.draft.copy(name = name)
    transition(user, dialog.copy(draft = draft), CreateS10nDialogEvent.EnteredName)
  }

  def saveCurrency(user: User, dialog: CreateS10nDialog, currency: CurrencyUnit): F[List[ReplyMessage]] = {
    val draft = dialog.draft.copy(currency = currency)
    transition(user, dialog.copy(draft = draft), CreateS10nDialogEvent.ChosenCurrency)
  }

  def saveAmount(user: User, dialog: CreateS10nDialog, amount: BigDecimal): F[List[ReplyMessage]] = {
    val draft = dialog.draft.copy(
      amount = SubscriptionAmount(Money.of(dialog.draft.currency, amount.bigDecimal).getAmountMinorLong)
    )
    transition(user, dialog.copy(draft = draft), CreateS10nDialogEvent.EnteredAmount)
  }

  def saveBillingPeriodDuration(user: User, dialog: CreateS10nDialog, duration: BillingPeriodDuration): F[List[ReplyMessage]] = {
    val draft = dialog.draft.copy(periodDuration = duration.some)
    transition(user, dialog.copy(draft = draft), CreateS10nDialogEvent.EnteredBillingPeriodDuration)
  }

  def saveBillingPeriodUnit(user: User, dialog: CreateS10nDialog, unit: BillingPeriodUnit): F[List[ReplyMessage]] = {
    val draft = dialog.draft.copy(periodUnit = unit.some)
    transition(user, dialog.copy(draft = draft), CreateS10nDialogEvent.ChosenBillingPeriodUnit)
  }

  def skipIsOneTime(user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]] =
    transition(user, dialog, CreateS10nDialogEvent.SkippedIsOneTime)

  def saveIsOneTime(user: User, dialog: CreateS10nDialog, oneTime: OneTimeSubscription): F[List[ReplyMessage]] = {
    val draft = dialog.draft.copy(oneTime = oneTime.some)
    val event = if (oneTime) CreateS10nDialogEvent.ChosenOneTime
    else CreateS10nDialogEvent.ChosenRecurring
    transition(user, dialog.copy(draft = draft), event)
  }

  def saveFirstPaymentDate(user: User, dialog: CreateS10nDialog, date: FirstPaymentDate): F[List[ReplyMessage]] = {
    val draft = dialog.draft.copy(firstPaymentDate = date.some)
    transition(user, dialog.copy(draft = draft), CreateS10nDialogEvent.ChosenFirstPaymentDate)
  }

  private def transition(user: User, dialog: CreateS10nDialog, event: CreateS10nDialogEvent): F[List[ReplyMessage]] = {
    val updatedDialog = dialog.copy(state = CreateS10nDialogState.transition(dialog.state, event))
    updatedDialog.state match {
      case finished @ CreateS10nDialogState.Finished =>
        subscriptionRepo.create(updatedDialog.draft).flatMap { s10n =>
          dialogEngine.reset(user, finished.message)
            .map((_, s10n))
        }
          .transact(xa)
          .flatMap { p =>
            s10nsListMessageService.createSubscriptionMessage(user, p._2, PageNumber(0))
              .map(List(p._1, _))
          }
      case _ =>
        val userWithUpdatedDialog = user.copy(dialog = updatedDialog.some)
        userRepo.createOrUpdate(userWithUpdatedDialog)
          .transact(xa) *>
          stateMessageService.getMessage(updatedDialog.state)
            .map(List(_))
    }
  }
}
