package ru.johnspade.s10ns.subscription.service.impl

import cats.Monad
import cats.implicits._
import cats.~>

import ru.johnspade.s10ns.bot
import ru.johnspade.s10ns.bot.CreateS10nDialog
import ru.johnspade.s10ns.bot.FirstPayment
import ru.johnspade.s10ns.bot.OneTime
import ru.johnspade.s10ns.bot.PeriodUnit
import ru.johnspade.s10ns.bot.ValidatorNec.ValidationResult
import ru.johnspade.s10ns.bot.ValidatorNec.validateAmount
import ru.johnspade.s10ns.bot.ValidatorNec.validateAmountString
import ru.johnspade.s10ns.bot.ValidatorNec.validateCurrency
import ru.johnspade.s10ns.bot.ValidatorNec.validateDuration
import ru.johnspade.s10ns.bot.ValidatorNec.validateDurationString
import ru.johnspade.s10ns.bot.ValidatorNec.validateNameLength
import ru.johnspade.s10ns.bot.ValidatorNec.validateText
import ru.johnspade.s10ns.bot.engine.DialogEngine
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.engine.StateMessageService
import ru.johnspade.s10ns.subscription.SubscriptionDraft
import ru.johnspade.s10ns.subscription.dialog.CreateS10nDialogState
import ru.johnspade.s10ns.subscription.service.CreateS10nDialogFsmService
import ru.johnspade.s10ns.subscription.service.CreateS10nDialogService
import ru.johnspade.s10ns.user.User

class DefaultCreateS10nDialogService[F[_]: Monad, D[_]: Monad](
    private val createS10nDialogFsmService: CreateS10nDialogFsmService[F],
    private val stateMessageService: StateMessageService[F, CreateS10nDialogState],
    private val dialogEngine: DialogEngine[F]
)(private implicit val transact: D ~> F)
    extends CreateS10nDialogService[F] {
  override def onCreateCommand(user: User): F[List[ReplyMessage]] = {
    val state = CreateS10nDialogState.Currency
    val dialog = CreateS10nDialog(
      state = state,
      draft = SubscriptionDraft.create(user.id)
    )
    stateMessageService.createReplyMessage(state).flatMap(dialogEngine.startDialog(user, dialog, _))
  }

  override def onCreateWithDefaultCurrencyCommand(user: User): F[List[ReplyMessage]] = {
    val state = CreateS10nDialogState.Name
    val dialog = bot.CreateS10nDialog(
      state = CreateS10nDialogState.Name,
      draft = SubscriptionDraft.create(user.id, user.defaultCurrency)
    )
    stateMessageService.createReplyMessage(state).flatMap(dialogEngine.startDialog(user, dialog, _))
  }

  override val saveDraft
      : PartialFunction[(User, CreateS10nDialog, Option[String]), F[ValidationResult[List[ReplyMessage]]]] = {
    case (user, dialog, text) if dialog.state == CreateS10nDialogState.Name =>
      validateText(text)
        .andThen(name => validateNameLength(name))
        .traverse(createS10nDialogFsmService.saveName(user, dialog, _))
    case (user, dialog, text) if dialog.state == CreateS10nDialogState.Currency =>
      validateText(text.map(_.trim.toUpperCase))
        .andThen(validateCurrency)
        .traverse(createS10nDialogFsmService.saveCurrency(user, dialog, _))
    case (user, dialog, text) if dialog.state == CreateS10nDialogState.Amount =>
      validateText(text)
        .andThen(validateAmountString)
        .andThen(validateAmount)
        .traverse(createS10nDialogFsmService.saveAmount(user, dialog, _))
    case (user, dialog, text) if dialog.state == CreateS10nDialogState.BillingPeriodDuration =>
      validateText(text)
        .andThen(validateDurationString)
        .andThen(duration => validateDuration(duration))
        .traverse(createS10nDialogFsmService.saveBillingPeriodDuration(user, dialog, _))
  }

  override def onBillingPeriodUnitCb(data: PeriodUnit, user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]] =
    createS10nDialogFsmService.saveBillingPeriodUnit(user, dialog, data.unit)

  override def onEveryMonthCb(user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]] =
    createS10nDialogFsmService.saveEveryMonth(user, dialog)

  override def onSkipIsOneTimeCb(user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]] =
    createS10nDialogFsmService.skipIsOneTime(user, dialog)

  override def onIsOneTimeCallback(data: OneTime, user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]] =
    createS10nDialogFsmService.saveIsOneTime(user, dialog, data.oneTime)

  override def onSkipFirstPaymentDateCb(user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]] =
    createS10nDialogFsmService.skipFirstPaymentDate(user, dialog)

  override def onFirstPaymentDateCb(data: FirstPayment, user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]] =
    createS10nDialogFsmService.saveFirstPaymentDate(user, dialog, data.date)
}
