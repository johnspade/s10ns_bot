package ru.johnspade.s10ns.subscription

import cats.effect.Sync
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import ru.johnspade.s10ns.common.Errors
import ru.johnspade.s10ns.common.ValidatorNec.{ValidationResult, validateAmount, validateAmountString, validateCurrency, validateDuration, validateDurationString, validateNameLength, validateText}
import ru.johnspade.s10ns.telegram.TelegramOps.TelegramUserOps
import ru.johnspade.s10ns.telegram.{BillingPeriodUnitCallbackData, FirstPaymentDateCallbackData, IsOneTimeCallbackData, ReplyMessage, StateMessageService}
import ru.johnspade.s10ns.user.{DialogType, SubscriptionDialogState, User, UserRepository}
import telegramium.bots.{CallbackQuery, MarkupRemoveKeyboard, ReplyKeyboardRemove}

class CreateS10nDialogService[F[_] : Sync](
  private val userRepo: UserRepository,
  private val createS10nDialogFsmService: CreateS10nDialogFsmService[F],
  private val stateMessageService: StateMessageService[F],
  private val xa: Transactor[F]
) {
  def onCreateCommand(user: User): F[ReplyMessage] = {
    val draft = SubscriptionDraft.create(user.id)
    val userWithDraft = user.copy(
      dialogType = DialogType.CreateSubscription.some,
      subscriptionDialogState = SubscriptionDialogState.Currency.some,
      subscriptionDraft = draft.some
    )
    userRepo.createOrUpdate(userWithDraft).transact(xa).flatMap { _ =>
      stateMessageService.getMessage(SubscriptionDialogState.Currency)
    }
  }

  def onCreateWithDefaultCurrencyCommand(user: User): F[ReplyMessage] = {
    val draft = SubscriptionDraft.create(user.id, user.defaultCurrency)
    val userWithDraft = user.copy(
      dialogType = DialogType.CreateSubscription.some,
      subscriptionDialogState = SubscriptionDialogState.Name.some,
      subscriptionDraft = draft.some
    )
    userRepo.createOrUpdate(userWithDraft).transact(xa).flatMap { _ =>
      stateMessageService.getMessage(SubscriptionDialogState.Name).map { reply =>
        reply.copy(markup = MarkupRemoveKeyboard(ReplyKeyboardRemove(removeKeyboard = true)).some)
      }
    }
  }

  val saveDraft: PartialFunction[(User, SubscriptionDialogState, Option[String]), F[ValidationResult[ReplyMessage]]] = {
    case (user, state, text) if state == SubscriptionDialogState.Name =>
      validateText(text)
        .andThen(name => validateNameLength(SubscriptionName(name)))
        .traverse(createS10nDialogFsmService.saveName(user, _))
    case (user, state, text) if state == SubscriptionDialogState.Currency =>
      validateText(text.map(_.trim.toUpperCase))
        .andThen(validateCurrency)
        .traverse(createS10nDialogFsmService.saveCurrency(user, _))
    case (user, state, text) if state == SubscriptionDialogState.Amount =>
      validateText(text)
        .andThen(validateAmountString)
        .andThen(validateAmount)
        .traverse(createS10nDialogFsmService.saveAmount(user, _))
    case (user, state, text) if state == SubscriptionDialogState.BillingPeriodDuration =>
      validateText(text)
        .andThen(validateDurationString)
        .andThen(duration => validateDuration(BillingPeriodDuration(duration)))
        .traverse(createS10nDialogFsmService.saveBillingPeriodDuration(user, _))
  }

  def onBillingPeriodUnitCb(cb: CallbackQuery): F[Either[String, ReplyMessage]] =
    handleCreateS10nCb(cb, user =>
      for {
        state <- user.subscriptionDialogState
        data <- cb.data if state == SubscriptionDialogState.BillingPeriodUnit
        unit = BillingPeriodUnitCallbackData.fromString(data).unit
      } yield createS10nDialogFsmService.saveBillingPeriodUnit(user, unit)
    )

  def onIsOneTimeCallback(cb: CallbackQuery): F[Either[String, ReplyMessage]] =
    handleCreateS10nCb(cb, user =>
      for {
        state <- user.subscriptionDialogState
        data <- cb.data if state == SubscriptionDialogState.IsOneTime
        oneTime = IsOneTimeCallbackData.fromString(data).oneTime
      } yield createS10nDialogFsmService.saveIsOneTime(user, oneTime)
    )

  def onFirstPaymentDateCallback(cb: CallbackQuery): F[Either[String, ReplyMessage]] =
    handleCreateS10nCb(cb, user =>
      for {
        state <- user.subscriptionDialogState
        data <- cb.data if state == SubscriptionDialogState.FirstPaymentDate
        firstPaymentDate = FirstPaymentDateCallbackData.fromString(data).date
      } yield createS10nDialogFsmService.saveFirstPaymentDate(user, FirstPaymentDate(firstPaymentDate))
    )

  private def handleCreateS10nCb(cb: CallbackQuery, saveDraft: User => Option[F[ReplyMessage]]): F[Either[String, ReplyMessage]] = {
    val tgUser = cb.from.toUser()
    userRepo.getOrCreate(tgUser)
      .map(user => saveDraft(user).sequence.map(_.toRight(Errors.default)))
      .transact(xa)
      .flatten
  }
}
