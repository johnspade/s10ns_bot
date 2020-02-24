package ru.johnspade.s10ns.subscription.service.impl

import cats.{Monad, ~>}
import com.softwaremill.quicklens._
import org.joda.money.Money
import ru.johnspade.s10ns.bot.ValidatorNec._
import ru.johnspade.s10ns.bot.engine.{ReplyMessage, StateMessageService, TransactionalDialogEngine}
import ru.johnspade.s10ns.bot.{EditS10nAmount, EditS10nAmountDialog}
import ru.johnspade.s10ns.subscription.dialog.{EditS10nAmountDialogEvent, EditS10nAmountDialogState}
import ru.johnspade.s10ns.subscription.repository.SubscriptionRepository
import ru.johnspade.s10ns.subscription.service.{EditS10nAmountDialogService, RepliesValidated, S10nsListMessageService}
import ru.johnspade.s10ns.user.{User, UserRepository}
import telegramium.bots.CallbackQuery

class DefaultEditS10nAmountDialogService[F[_] : Monad, D[_] : Monad](
  s10nsListMessageService: S10nsListMessageService[F],
  stateMessageService: StateMessageService[F, EditS10nAmountDialogState],
  userRepo: UserRepository[D],
  s10nRepo: SubscriptionRepository[D],
  dialogEngine: TransactionalDialogEngine[F, D]
)(implicit transact: D ~> F)
  extends EditS10nDialogService[F, D, EditS10nAmountDialogState](
    s10nsListMessageService, stateMessageService, userRepo, s10nRepo, dialogEngine
  ) with EditS10nAmountDialogService[F] {
  override def saveAmount(user: User, dialog: EditS10nAmountDialog, text: Option[String]): F[RepliesValidated] =
    validateText(text)
      .andThen(validateAmountString)
      .andThen(amount => validateAmount(amount))
      .traverse(saveAmount(user, dialog, _))

  override def onEditS10nAmountCb(user: User, cb: CallbackQuery, data: EditS10nAmount): F[List[ReplyMessage]] = {
    val start = EditS10nAmountDialogState.Amount
    onEditS10nDialogCb(
      user = user,
      cb = cb,
      s10nId = data.subscriptionId,
      state = start,
      createDialog = EditS10nAmountDialog(start, _)
    )
  }

  private def saveAmount(user: User, dialog: EditS10nAmountDialog, amount: BigDecimal): F[List[ReplyMessage]] = {
    val updatedDialog = dialog.modify(_.draft.amount).setTo(Money.of(dialog.draft.amount.getCurrencyUnit, amount.bigDecimal))
    transition(user, updatedDialog)(EditS10nAmountDialogEvent.EnteredAmount)
  }
}
