package ru.johnspade.s10ns.subscription

import cats.effect.Sync
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.chrisdavenport.log4cats.Logger
import org.joda.money.{CurrencyUnit, Money}
import ru.johnspade.s10ns.common.Errors
import ru.johnspade.s10ns.telegram.{ReplyMessage, StateMessageService}
import ru.johnspade.s10ns.user.{CreateS10nDialogEvent, CreateS10nDialogState, DialogType, User, UserRepository}

class CreateS10nDialogFsmService[F[_] : Sync : Logger](
  private val subscriptionRepo: SubscriptionRepository,
  private val userRepo: UserRepository,
  private val xa: Transactor[F],
  private val stateMessageService: StateMessageService[F]
) {

  // todo: log
  private def getSubscriptionDraft(user: User) = user.subscriptionDraft.getOrElse(SubscriptionDraft.create(user.id))

  def saveName(user: User, name: SubscriptionName): F[ReplyMessage] = {
    val draft = getSubscriptionDraft(user).copy(name = name)
    transition(user, draft, CreateS10nDialogEvent.EnteredName)
  }

  def saveCurrency(user: User, currency: CurrencyUnit): F[ReplyMessage] = {
    val draft = getSubscriptionDraft(user).copy(currency = currency)
    transition(user, draft, CreateS10nDialogEvent.ChosenCurrency)
  }

  def saveAmount(user: User, amount: BigDecimal): F[ReplyMessage] = {
    val userDraft = getSubscriptionDraft(user)
    val draft = getSubscriptionDraft(user).copy(
      amount = SubscriptionAmount(Money.of(userDraft.currency, amount.bigDecimal).getAmountMinorLong)
    )
    transition(user, draft, CreateS10nDialogEvent.EnteredAmount)
  }

  def saveBillingPeriodDuration(user: User, duration: BillingPeriodDuration): F[ReplyMessage] = {
    val draft = getSubscriptionDraft(user).copy(periodDuration = duration.some)
    transition(user, draft, CreateS10nDialogEvent.EnteredBillingPeriodDuration)
  }

  def saveBillingPeriodUnit(user: User, unit: BillingPeriodUnit): F[ReplyMessage] = {
    val draft = getSubscriptionDraft(user).copy(periodUnit = unit.some)
    transition(user, draft, CreateS10nDialogEvent.ChosenBillingPeriodUnit)
  }

  def saveIsOneTime(user: User, oneTime: OneTimeSubscription): F[ReplyMessage] = {
    val draft = getSubscriptionDraft(user).copy(oneTime = oneTime)
    val event = if (oneTime.value) CreateS10nDialogEvent.ChosenOneTime
    else CreateS10nDialogEvent.ChosenRecurring
    transition(user, draft, event)
  }

  def saveFirstPaymentDate(user: User, date: FirstPaymentDate): F[ReplyMessage] = {
    val draft = getSubscriptionDraft(user).copy(firstPaymentDate = date.some)
    transition(user, draft, CreateS10nDialogEvent.ChosenFirstPaymentDate)
  }

  private def transition(user: User, draft: SubscriptionDraft, event: CreateS10nDialogEvent): F[ReplyMessage] = {
    val newState = user.dialogType.flatMap {
      case DialogType.CreateSubscription =>
        user.subscriptionDialogState.map(CreateS10nDialogState.transition(_, event))
      case _ => Option.empty[CreateS10nDialogState]
    }
    val savedUser = newState match {
      case Some(CreateS10nDialogState.Finished) =>
        val userWithNewState = user.copy(dialogType = None, subscriptionDraft = None)
        subscriptionRepo.create(draft)
          .productR(userRepo.createOrUpdate(userWithNewState))
          .transact(xa)
      case _ =>
        val userWithNewState = user.copy(subscriptionDialogState = newState, subscriptionDraft = draft.some)
        userRepo.createOrUpdate(userWithNewState).transact(xa)
    }
    savedUser *> newState.map(stateMessageService.getMessage).getOrElse(Sync[F].delay(ReplyMessage(Errors.default)))
  }
}
