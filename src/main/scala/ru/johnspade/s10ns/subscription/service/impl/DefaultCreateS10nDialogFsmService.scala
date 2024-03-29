package ru.johnspade.s10ns.subscription.service.impl

import java.time.LocalDate

import cats.Monad
import cats.implicits._
import cats.~>

import org.joda.money.CurrencyUnit
import org.joda.money.Money

import ru.johnspade.s10ns.bot.CreateS10nDialog
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.engine.StateMessageService
import ru.johnspade.s10ns.bot.engine.TransactionalDialogEngine
import ru.johnspade.s10ns.subscription.BillingPeriodUnit
import ru.johnspade.s10ns.subscription.dialog.CreateS10nDialogEvent
import ru.johnspade.s10ns.subscription.dialog.CreateS10nDialogState
import ru.johnspade.s10ns.subscription.repository.SubscriptionRepository
import ru.johnspade.s10ns.subscription.service.CreateS10nDialogFsmService
import ru.johnspade.s10ns.subscription.service.S10nsListMessageService
import ru.johnspade.s10ns.user.User
import ru.johnspade.s10ns.user.UserRepository

class DefaultCreateS10nDialogFsmService[F[_]: Monad, D[_]: Monad](
    private val subscriptionRepo: SubscriptionRepository[D],
    private val userRepo: UserRepository[D],
    private val dialogEngine: TransactionalDialogEngine[F, D],
    private val s10nsListMessageService: S10nsListMessageService[F],
    private val stateMessageService: StateMessageService[F, CreateS10nDialogState]
)(private implicit val transact: D ~> F)
    extends CreateS10nDialogFsmService[F] {

  override def saveName(user: User, dialog: CreateS10nDialog, name: String): F[List[ReplyMessage]] = {
    val draft = dialog.draft.copy(name = name)
    transition(user, dialog.copy(draft = draft), CreateS10nDialogEvent.EnteredName)
  }

  override def saveCurrency(user: User, dialog: CreateS10nDialog, currency: CurrencyUnit): F[List[ReplyMessage]] = {
    val draft = dialog.draft.copy(currency = currency)
    transition(user, dialog.copy(draft = draft), CreateS10nDialogEvent.ChosenCurrency)
  }

  override def saveAmount(user: User, dialog: CreateS10nDialog, amount: BigDecimal): F[List[ReplyMessage]] = {
    val draft = dialog.draft.copy(
      amount = Money.of(dialog.draft.currency, amount.bigDecimal).getAmountMinorLong
    )
    transition(user, dialog.copy(draft = draft), CreateS10nDialogEvent.EnteredAmount)
  }

  override def saveBillingPeriodDuration(
      user: User,
      dialog: CreateS10nDialog,
      duration: Int
  ): F[List[ReplyMessage]] = {
    val draft = dialog.draft.copy(periodDuration = duration.some)
    transition(user, dialog.copy(draft = draft), CreateS10nDialogEvent.EnteredBillingPeriodDuration)
  }

  override def saveBillingPeriodUnit(
      user: User,
      dialog: CreateS10nDialog,
      unit: BillingPeriodUnit
  ): F[List[ReplyMessage]] = {
    val draft = dialog.draft.copy(periodUnit = unit.some)
    transition(user, dialog.copy(draft = draft), CreateS10nDialogEvent.ChosenBillingPeriodUnit)
  }

  override def saveEveryMonth(user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]] = {
    val draft = dialog.draft.copy(
      periodUnit = BillingPeriodUnit.Month.some,
      periodDuration = 1.some
    )
    transition(user, dialog.copy(draft = draft), CreateS10nDialogEvent.ChosenEveryMonth)
  }

  override def skipIsOneTime(user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]] =
    transition(user, dialog, CreateS10nDialogEvent.SkippedIsOneTime)

  override def saveIsOneTime(
      user: User,
      dialog: CreateS10nDialog,
      oneTime: Boolean
  ): F[List[ReplyMessage]] = {
    val draft = dialog.draft.copy(oneTime = oneTime.some)
    val event =
      if (oneTime) CreateS10nDialogEvent.ChosenOneTime
      else CreateS10nDialogEvent.ChosenRecurring
    transition(user, dialog.copy(draft = draft), event)
  }

  override def skipFirstPaymentDate(user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]] =
    transition(user, dialog, CreateS10nDialogEvent.SkippedFirstPaymentDate)

  override def saveFirstPaymentDate(
      user: User,
      dialog: CreateS10nDialog,
      date: LocalDate
  ): F[List[ReplyMessage]] = {
    val draft = dialog.draft.copy(firstPaymentDate = date.some)
    transition(user, dialog.copy(draft = draft), CreateS10nDialogEvent.ChosenFirstPaymentDate)
  }

  private def transition(user: User, dialog: CreateS10nDialog, event: CreateS10nDialogEvent): F[List[ReplyMessage]] = {
    val updatedDialog = dialog.copy(state = CreateS10nDialogState.transition(dialog.state, event))
    updatedDialog.state match {
      case finished @ CreateS10nDialogState.Finished =>
        val sendNotifications = user.notifyByDefault.getOrElse(false)
        transact {
          subscriptionRepo
            .create(updatedDialog.draft.copy(sendNotifications = sendNotifications))
            .flatMap { s10n =>
              dialogEngine
                .reset(user, finished.message)
                .map((_, s10n))
            }
        }
          .flatMap { p =>
            s10nsListMessageService
              .createSubscriptionMessage(user.defaultCurrency, p._2, 0)
              .map(List(p._1, _))
          }
      case _ =>
        val userWithUpdatedDialog = user.copy(dialog = updatedDialog.some)
        transact(userRepo.createOrUpdate(userWithUpdatedDialog))
          .flatMap(_ => stateMessageService.createReplyMessage(updatedDialog.state).map(List(_)))
    }
  }
}
