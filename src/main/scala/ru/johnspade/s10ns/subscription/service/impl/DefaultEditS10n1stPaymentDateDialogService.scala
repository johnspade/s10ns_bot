package ru.johnspade.s10ns.subscription.service.impl

import cats.syntax.option._
import cats.{Monad, ~>}
import com.softwaremill.quicklens._
import ru.johnspade.s10ns.bot.engine.{ReplyMessage, StateMessageService, TransactionalDialogEngine}
import ru.johnspade.s10ns.bot.{EditS10nFirstPaymentDate, EditS10nFirstPaymentDateDialog, FirstPayment}
import ru.johnspade.s10ns.subscription.dialog.{EditS10nFirstPaymentDateDialogEvent, EditS10nFirstPaymentDateDialogState}
import ru.johnspade.s10ns.subscription.repository.SubscriptionRepository
import ru.johnspade.s10ns.subscription.service.{EditS10n1stPaymentDateDialogService, S10nsListMessageService}
import ru.johnspade.s10ns.user.{User, UserRepository}

class DefaultEditS10n1stPaymentDateDialogService[F[_] : Monad, D[_] : Monad](
  s10nsListMessageService: S10nsListMessageService[F],
  stateMessageService: StateMessageService[F, EditS10nFirstPaymentDateDialogState],
  userRepo: UserRepository[D],
  s10nRepo: SubscriptionRepository[D],
  dialogEngine: TransactionalDialogEngine[F, D]
)(implicit transact: D ~> F)
  extends EditS10nDialogService[F, D, EditS10nFirstPaymentDateDialogState](
    s10nsListMessageService, stateMessageService, userRepo, s10nRepo, dialogEngine
  ) with EditS10n1stPaymentDateDialogService[F] {
  override def onEditS10nFirstPaymentDateCb(user: User, data: EditS10nFirstPaymentDate): F[List[ReplyMessage]] = {
    val start = EditS10nFirstPaymentDateDialogState.FirstPaymentDate
    onEditS10nDialogCb(
      user = user,
      s10nId = data.subscriptionId,
      state = start,
      createDialog = s10n => EditS10nFirstPaymentDateDialog(start, s10n)
    )
  }

  override def saveFirstPaymentDate(data: FirstPayment, user: User, dialog: EditS10nFirstPaymentDateDialog): F[List[ReplyMessage]] = {
    val updatedDialog = dialog.modify(_.draft.firstPaymentDate).setTo(data.date.some)
    transition(user, updatedDialog)(EditS10nFirstPaymentDateDialogEvent.ChosenFirstPaymentDate)
  }

  override def removeFirstPaymentDate(user: User, dialog: EditS10nFirstPaymentDateDialog): F[List[ReplyMessage]] = {
    val updatedDialog = dialog.modify(_.draft.firstPaymentDate).setTo(None)
    transition(user, updatedDialog)(EditS10nFirstPaymentDateDialogEvent.RemovedFirstPaymentDate)
  }
}
