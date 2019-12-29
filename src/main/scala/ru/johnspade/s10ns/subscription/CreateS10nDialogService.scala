package ru.johnspade.s10ns.subscription

import cats.effect.Sync
import cats.implicits._
import doobie.util.transactor.Transactor
import ru.johnspade.s10ns.common.ValidatorNec.{ValidationResult, validateAmount, validateAmountString, validateCurrency, validateDuration, validateDurationString, validateNameLength, validateText}
import ru.johnspade.s10ns.subscription.tags._
import ru.johnspade.s10ns.telegram.{DialogEngine, FirstPayment, OneTime, PeriodUnit, ReplyMessage}
import ru.johnspade.s10ns.user.{CreateS10nDialog, CreateS10nDialogState, User, UserRepository}
import telegramium.bots.CallbackQuery

class CreateS10nDialogService[F[_] : Sync](
  private val userRepo: UserRepository,
  private val createS10nDialogFsmService: CreateS10nDialogFsmService[F],
  private val stateMessageService: StateMessageService[F],
  private val dialogEngine: DialogEngine[F]
)(private implicit val xa: Transactor[F]) {
  def onCreateCommand(user: User): F[ReplyMessage] = {
    val state = CreateS10nDialogState.Currency
    val dialog = CreateS10nDialog(
      state = state,
      draft = SubscriptionDraft.create(user.id)
    )
    stateMessageService.getMessage(state)
      .flatMap(dialogEngine.startDialog(user, dialog, _))
  }

  def onCreateWithDefaultCurrencyCommand(user: User): F[ReplyMessage] = {
    val state = CreateS10nDialogState.Name
    val dialog = CreateS10nDialog(
      state = CreateS10nDialogState.Name,
      draft = SubscriptionDraft.create(user.id, user.defaultCurrency)
    )
    stateMessageService.getMessage(state)
      .flatMap(dialogEngine.startDialog(user, dialog, _))
  }

  val saveDraft: PartialFunction[(User, CreateS10nDialog, Option[String]), F[ValidationResult[List[ReplyMessage]]]] = {
    case (user, dialog, text) if dialog.state == CreateS10nDialogState.Name =>
      validateText(text)
        .andThen(name => validateNameLength(SubscriptionName(name)))
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
        .andThen(duration => validateDuration(BillingPeriodDuration(duration)))
        .traverse(createS10nDialogFsmService.saveBillingPeriodDuration(user, dialog, _))
  }

  def onBillingPeriodUnitCb(cb: CallbackQuery, data: PeriodUnit, user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]] =
    createS10nDialogFsmService.saveBillingPeriodUnit(user, dialog, data.unit)

  def onIsOneTimeCallback(cb: CallbackQuery, data: OneTime, user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]] =
    createS10nDialogFsmService.saveIsOneTime(user, dialog, data.oneTime)

  def onFirstPaymentDateCallback(cb: CallbackQuery, data: FirstPayment, user: User, dialog: CreateS10nDialog): F[List[ReplyMessage]] =
    createS10nDialogFsmService.saveFirstPaymentDate(user, dialog, data.date)
}
