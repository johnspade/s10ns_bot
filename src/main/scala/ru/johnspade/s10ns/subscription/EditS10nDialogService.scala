package ru.johnspade.s10ns.subscription

import cats.effect.Sync
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import ru.johnspade.s10ns.common.Errors
import ru.johnspade.s10ns.common.ValidatorNec._
import ru.johnspade.s10ns.subscription.tags._
import ru.johnspade.s10ns.telegram.{DialogEngine, EditS10nAmount, EditS10nBillingPeriod, EditS10nName, EditS10nOneTime, OneTime, PeriodUnit, ReplyMessage}
import ru.johnspade.s10ns.user.{EditS10nAmountDialog, EditS10nAmountDialogState, EditS10nBillingPeriodDialog, EditS10nBillingPeriodDialogState, EditS10nDialog, EditS10nDialogFsmService, EditS10nNameDialog, EditS10nNameDialogState, EditS10nOneTimeDialog, EditS10nOneTimeDialogState, User}
import telegramium.bots.CallbackQuery

class EditS10nDialogService[F[_] : Sync](
  private val s10nRepo: SubscriptionRepository,
  private val editS10nDialogFsmService: EditS10nDialogFsmService[F],
  private val stateMessageService: StateMessageService[F],
  private val dialogEngine: DialogEngine[F]
)(private implicit val xa: Transactor[F]) {
  type RepliesValidated = ValidationResult[List[ReplyMessage]]

  def onEditS10nNameCb(user: User, cb: CallbackQuery, data: EditS10nName): F[ReplyMessage] =
    onEditS10nDialogCb(
      user = user,
      cb = cb,
      s10nId = data.subscriptionId,
      message = stateMessageService.getTextMessage(EditS10nNameDialogState.Name),
      createDialog =
        s10n => EditS10nNameDialog(
          state = EditS10nNameDialogState.Name,
          draft = s10n
        )
    )

  def saveName(user: User, dialog: EditS10nNameDialog, text: Option[String]): F[RepliesValidated] =
    validateText(text)
      .andThen(name => validateNameLength(SubscriptionName(name)))
      .traverse(editS10nDialogFsmService.saveName(user, dialog, _))

  def onEditS10nAmountCb(user: User, cb: CallbackQuery, data: EditS10nAmount): F[ReplyMessage] =
    onEditS10nDialogCb(
      user = user,
      cb = cb,
      s10nId = data.subscriptionId,
      message = stateMessageService.getMessage(EditS10nAmountDialogState.Currency),
      createDialog =
        s10n => EditS10nAmountDialog(
          EditS10nAmountDialogState.Currency,
          s10n
        )
    )

  def saveCurrency(user: User, dialog: EditS10nAmountDialog, text: Option[String]): F[RepliesValidated] =
    validateText(text)
      .andThen(validateCurrency)
      .traverse(editS10nDialogFsmService.saveCurrency(user, dialog, _))

  def saveAmount(user: User, dialog: EditS10nAmountDialog, text: Option[String]): F[RepliesValidated] =
    validateText(text)
      .andThen(validateAmountString)
      .andThen(amount => validateAmount(amount))
      .traverse(editS10nDialogFsmService.saveAmount(user, dialog, _))

  def saveIsOneTime(cb: CallbackQuery, data: OneTime, user: User, dialog: EditS10nOneTimeDialog): F[List[ReplyMessage]] =
    editS10nDialogFsmService.saveIsOneTime(user, dialog, data.oneTime)

  def onEditS10nOneTimeCb(user: User, cb: CallbackQuery, data: EditS10nOneTime): F[ReplyMessage] =
    onEditS10nDialogCb(
      user = user,
      cb = cb,
      s10nId = data.subscriptionId,
      message = stateMessageService.getMessage(EditS10nOneTimeDialogState.IsOneTime),
      createDialog =
        s10n => EditS10nOneTimeDialog(
          EditS10nOneTimeDialogState.IsOneTime,
          s10n
        )
    )

  def saveBillingPeriodUnit(cb: CallbackQuery, data: PeriodUnit, user: User, dialog: EditS10nOneTimeDialog): F[List[ReplyMessage]] =
    editS10nDialogFsmService.saveBillingPeriodUnit(user, dialog, data.unit)

  def saveBillingPeriodDuration(user: User, dialog: EditS10nOneTimeDialog, text: Option[String]): F[RepliesValidated] =
    validateText(text)
      .andThen(validateDurationString)
      .andThen(duration => validateDuration(BillingPeriodDuration(duration)))
      .traverse(editS10nDialogFsmService.saveBillingPeriodDuration(user, dialog, _))

  def onEditS10nBillingPeriodCb(user: User, cb: CallbackQuery, data: EditS10nBillingPeriod): F[ReplyMessage] =
    onEditS10nDialogCb(
      user = user,
      cb = cb,
      s10nId = data.subscriptionId,
      message = stateMessageService.getMessage(EditS10nBillingPeriodDialogState.BillingPeriodUnit),
      createDialog =
        s10n => EditS10nBillingPeriodDialog(
          EditS10nBillingPeriodDialogState.BillingPeriodUnit,
          s10n
        )
    )

  def saveBillingPeriodUnit(cb: CallbackQuery, data: PeriodUnit, user: User, dialog: EditS10nBillingPeriodDialog): F[List[ReplyMessage]] =
    editS10nDialogFsmService.saveBillingPeriodUnit(user, dialog, data.unit)

  def saveBillingPeriodDuration(user: User, dialog: EditS10nBillingPeriodDialog, text: Option[String]): F[RepliesValidated] =
    validateText(text)
      .andThen(validateDurationString)
      .andThen(duration => validateDuration(BillingPeriodDuration(duration)))
      .traverse(editS10nDialogFsmService.saveBillingPeriodDuration(user, dialog, _))

  private def onEditS10nDialogCb(
    user: User,
    cb: CallbackQuery,
    s10nId: SubscriptionId,
    message: F[ReplyMessage],
    createDialog: Subscription => EditS10nDialog
  ): F[ReplyMessage] = {
    def saveAndReply(s10n: Subscription) = {
      val checkUserAndGetMessage = Either.cond(
        s10n.userId == user.id,
        message,
        Errors.accessDenied
      )
      checkUserAndGetMessage match {
        case Left(error) => Sync[F].pure(ReplyMessage(error))
        case Right(reply) =>
          val dialog = createDialog(s10n)
          reply.flatMap(dialogEngine.startDialog(user, dialog, _))
      }
    }

    for {
      s10nOpt <- s10nRepo.getById(s10nId).transact(xa)
      replyOpt <- s10nOpt.traverse(saveAndReply)
    } yield replyOpt.getOrElse(ReplyMessage(Errors.notFound))
  }
}
