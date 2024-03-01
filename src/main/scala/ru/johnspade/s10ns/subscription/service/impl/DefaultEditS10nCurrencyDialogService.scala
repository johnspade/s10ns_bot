package ru.johnspade.s10ns.subscription.service.impl

import cats.Monad
import cats.~>

import com.softwaremill.quicklens._
import org.joda.money.CurrencyUnit
import org.joda.money.Money

import ru.johnspade.s10ns.bot.EditS10nCurrency
import ru.johnspade.s10ns.bot.EditS10nCurrencyDialog
import ru.johnspade.s10ns.bot.ValidatorNec._
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.engine.StateMessageService
import ru.johnspade.s10ns.bot.engine.TransactionalDialogEngine
import ru.johnspade.s10ns.subscription.dialog.EditS10nCurrencyDialogEvent
import ru.johnspade.s10ns.subscription.dialog.EditS10nCurrencyDialogState
import ru.johnspade.s10ns.subscription.repository.SubscriptionRepository
import ru.johnspade.s10ns.subscription.service.EditS10nCurrencyDialogService
import ru.johnspade.s10ns.subscription.service.RepliesValidated
import ru.johnspade.s10ns.subscription.service.S10nsListMessageService
import ru.johnspade.s10ns.user.User
import ru.johnspade.s10ns.user.UserRepository

class DefaultEditS10nCurrencyDialogService[F[_]: Monad, D[_]: Monad](
    s10nsListMessageService: S10nsListMessageService[F],
    stateMessageService: StateMessageService[F, EditS10nCurrencyDialogState],
    userRepo: UserRepository[D],
    s10nRepo: SubscriptionRepository[D],
    dialogEngine: TransactionalDialogEngine[F, D]
)(implicit transact: D ~> F)
    extends EditS10nDialogService[F, D, EditS10nCurrencyDialogState](
      s10nsListMessageService,
      stateMessageService,
      userRepo,
      s10nRepo,
      dialogEngine
    )
    with EditS10nCurrencyDialogService[F] {
  override def onEditS10nCurrencyCb(user: User, data: EditS10nCurrency): F[List[ReplyMessage]] =
    onEditS10nDialogCb(
      user = user,
      s10nId = data.subscriptionId,
      state = EditS10nCurrencyDialogState.Currency,
      createDialog = s10n =>
        EditS10nCurrencyDialog(
          EditS10nCurrencyDialogState.Currency,
          s10n
        )
    )

  override def saveCurrency(user: User, dialog: EditS10nCurrencyDialog, text: Option[String]): F[RepliesValidated] =
    validateText(text)
      .andThen(validateCurrency)
      .traverse(saveCurrency(user, dialog, _))

  override def saveAmount(user: User, dialog: EditS10nCurrencyDialog, text: Option[String]): F[RepliesValidated] =
    validateText(text)
      .andThen(validateAmountString)
      .andThen(amount => validateAmount(amount))
      .traverse(saveAmount(user, dialog, _))

  private def saveCurrency(
      user: User,
      dialog: EditS10nCurrencyDialog,
      currency: CurrencyUnit
  ): F[List[ReplyMessage]] = {
    val updatedDialog = dialog.modify(_.draft.amount).setTo(Money.zero(currency))
    transition(user, updatedDialog)(EditS10nCurrencyDialogEvent.ChosenCurrency)
  }

  private def saveAmount(user: User, dialog: EditS10nCurrencyDialog, amount: BigDecimal): F[List[ReplyMessage]] = {
    val updatedDialog =
      dialog.modify(_.draft.amount).setTo(Money.of(dialog.draft.amount.getCurrencyUnit, amount.bigDecimal))
    transition(user, updatedDialog)(EditS10nCurrencyDialogEvent.EnteredAmount)
  }
}
