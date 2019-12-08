package ru.johnspade.s10ns.subscription

import cats.effect.Sync
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import ru.johnspade.s10ns.common.Errors
import ru.johnspade.s10ns.common.ValidatorNec.{ValidationResult, validateAmount, validateAmountString, validateCurrency, validateDuration, validateDurationString, validateNameLength, validateText}
import ru.johnspade.s10ns.subscription.tags._
import ru.johnspade.s10ns.telegram.TelegramOps.TelegramUserOps
import ru.johnspade.s10ns.telegram.{FirstPayment, OneTime, PeriodUnit, ReplyMessage}
import ru.johnspade.s10ns.user.{CreateS10nDialog, CreateS10nDialogState, User, UserRepository}
import telegramium.bots.{CallbackQuery, MarkupRemoveKeyboard, ReplyKeyboardRemove}

class CreateS10nDialogService[F[_] : Sync](
  private val userRepo: UserRepository,
  private val createS10nDialogFsmService: CreateS10nDialogFsmService[F],
  private val stateMessageService: StateMessageService[F]
)(private implicit val xa: Transactor[F]) {
  def onCreateCommand(user: User): F[ReplyMessage] = {
    val userWithDraft = user.copy(
      dialog = CreateS10nDialog(
        state = CreateS10nDialogState.Currency,
        draft = SubscriptionDraft.create(user.id)
      ).some
    )
    userRepo.createOrUpdate(userWithDraft).transact(xa).flatMap { _ =>
      stateMessageService.getMessage(CreateS10nDialogState.Currency)
    }
  }

  def onCreateWithDefaultCurrencyCommand(user: User): F[ReplyMessage] = {
    val userWithDraft = user.copy(
      dialog = CreateS10nDialog(
        state = CreateS10nDialogState.Name,
        draft = SubscriptionDraft.create(user.id, user.defaultCurrency)
      ).some
    )
    userRepo.createOrUpdate(userWithDraft).transact(xa).flatMap { _ =>
      stateMessageService.getMessage(CreateS10nDialogState.Name).map { reply =>
        reply.copy(markup = MarkupRemoveKeyboard(ReplyKeyboardRemove(removeKeyboard = true)).some)
      }
    }
  }

  val saveDraft: PartialFunction[(User, CreateS10nDialog, Option[String]), F[ValidationResult[ReplyMessage]]] = {
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

  def onBillingPeriodUnitCb(cb: CallbackQuery, data: PeriodUnit): F[Either[String, ReplyMessage]] =
    handleCreateS10nCb(cb, user =>
      user.dialog.collect {
        case dialog @ CreateS10nDialog(CreateS10nDialogState.BillingPeriodUnit, _) =>
          createS10nDialogFsmService.saveBillingPeriodUnit(user, dialog, data.unit)
      }
    )

  def onIsOneTimeCallback(cb: CallbackQuery, data: OneTime): F[Either[String, ReplyMessage]] =
    handleCreateS10nCb(cb, user =>
      user.dialog.collect {
        case dialog @ CreateS10nDialog(CreateS10nDialogState.IsOneTime, _) =>
          createS10nDialogFsmService.saveIsOneTime(user, dialog, data.oneTime)
      }
    )

  def onFirstPaymentDateCallback(cb: CallbackQuery, data: FirstPayment): F[Either[String, ReplyMessage]] =
    handleCreateS10nCb(cb, user =>
      user.dialog.collect {
        case dialog @ CreateS10nDialog(CreateS10nDialogState.FirstPaymentDate, _) =>
          createS10nDialogFsmService.saveFirstPaymentDate(user, dialog, data.date)
      }
    )

  private def handleCreateS10nCb(cb: CallbackQuery, saveDraft: User => Option[F[ReplyMessage]]): F[Either[String, ReplyMessage]] = {
    val tgUser = cb.from.toUser()
    userRepo.getOrCreate(tgUser)
      .map(user => saveDraft(user).sequence.map(_.toRight(Errors.default)))
      .transact(xa)
      .flatten
  }
}
