package ru.johnspade.s10ns.subscription

import cats.effect.Sync
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.chrisdavenport.log4cats.Logger
import org.joda.money.{CurrencyUnit, Money}
import ru.johnspade.s10ns.subscription.tags._
import ru.johnspade.s10ns.telegram.ReplyMessage
import ru.johnspade.s10ns.user.{CreateS10nDialog, CreateS10nDialogEvent, CreateS10nDialogState, User, UserRepository}

class CreateS10nDialogFsmService[F[_] : Sync : Logger](
  private val subscriptionRepo: SubscriptionRepository,
  private val userRepo: UserRepository,
  private val stateMessageService: StateMessageService[F]
)(private implicit val xa: Transactor[F]) {

  def saveName(user: User, dialog: CreateS10nDialog, name: SubscriptionName): F[ReplyMessage] = {
    val draft = dialog.draft.copy(name = name)
    transition(user, dialog.copy(draft = draft), CreateS10nDialogEvent.EnteredName)
  }

  def saveCurrency(user: User, dialog: CreateS10nDialog, currency: CurrencyUnit): F[ReplyMessage] = {
    val draft = dialog.draft.copy(currency = currency)
    transition(user, dialog.copy(draft = draft), CreateS10nDialogEvent.ChosenCurrency)
  }

  def saveAmount(user: User, dialog: CreateS10nDialog, amount: BigDecimal): F[ReplyMessage] = {
    val draft = dialog.draft.copy(
      amount = SubscriptionAmount(Money.of(dialog.draft.currency, amount.bigDecimal).getAmountMinorLong)
    )
    transition(user, dialog.copy(draft = draft), CreateS10nDialogEvent.EnteredAmount)
  }

  def saveBillingPeriodDuration(user: User, dialog: CreateS10nDialog, duration: BillingPeriodDuration): F[ReplyMessage] = {
    val draft = dialog.draft.copy(periodDuration = duration.some)
    transition(user, dialog.copy(draft = draft), CreateS10nDialogEvent.EnteredBillingPeriodDuration)
  }

  def saveBillingPeriodUnit(user: User, dialog: CreateS10nDialog, unit: BillingPeriodUnit): F[ReplyMessage] = {
    val draft = dialog.draft.copy(periodUnit = unit.some)
    transition(user, dialog.copy(draft = draft), CreateS10nDialogEvent.ChosenBillingPeriodUnit)
  }

  def saveIsOneTime(user: User, dialog: CreateS10nDialog, oneTime: OneTimeSubscription): F[ReplyMessage] = {
    val draft = dialog.draft.copy(oneTime = oneTime)
    val event = if (oneTime) CreateS10nDialogEvent.ChosenOneTime
    else CreateS10nDialogEvent.ChosenRecurring
    transition(user, dialog.copy(draft = draft), event)
  }

  def saveFirstPaymentDate(user: User, dialog: CreateS10nDialog, date: FirstPaymentDate): F[ReplyMessage] = {
    val draft = dialog.draft.copy(firstPaymentDate = date.some)
    transition(user, dialog.copy(draft = draft), CreateS10nDialogEvent.ChosenFirstPaymentDate)
  }

  private def transition(user: User, dialog: CreateS10nDialog, event: CreateS10nDialogEvent): F[ReplyMessage] = {
    val updatedDialog = dialog.copy(state = CreateS10nDialogState.transition(dialog.state, event))
    val saveUser = updatedDialog.state match {
      case CreateS10nDialogState.Finished =>
        val userWithoutDialog = user.copy(dialog = None)
        subscriptionRepo.create(updatedDialog.draft)
          .productR(userRepo.createOrUpdate(userWithoutDialog))
          .transact(xa)
      case _ =>
        val userWithUpdatedDialog = user.copy(dialog = updatedDialog.some)
        userRepo.createOrUpdate(userWithUpdatedDialog).transact(xa)
    }
    saveUser *> stateMessageService.getMessage(updatedDialog.state)
  }
}
