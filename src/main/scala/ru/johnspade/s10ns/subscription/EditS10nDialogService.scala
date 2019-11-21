package ru.johnspade.s10ns.subscription

import cats.effect.Sync
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import ru.johnspade.s10ns.common.Errors
import ru.johnspade.s10ns.common.ValidatorNec.{ValidationResult, validateNameLength, validateText}
import ru.johnspade.s10ns.telegram.TelegramOps.TelegramUserOps
import ru.johnspade.s10ns.telegram.{EditS10nNameCbData, ReplyMessage, StateMessageService}
import ru.johnspade.s10ns.user.{DialogType, EditS10nDialogFsmService, EditS10nNameDialogState, User, UserId, UserRepository}
import telegramium.bots.CallbackQuery

class EditS10nDialogService[F[_] : Sync](
  private val userRepo: UserRepository,
  private val s10nRepo: SubscriptionRepository,
  private val xa: Transactor[F],
  private val editS10nDialogFsmService: EditS10nDialogFsmService[F],
  private val stateMessageService: StateMessageService[F]
) {
  def onEditS10nNameCb(cb: CallbackQuery): F[Either[String, ReplyMessage]] = { // todo refactor
    def checkUserAndGetMessage(subscription: Subscription) =
      Either.cond(
        subscription.userId == UserId(cb.from.id),
        stateMessageService.getMessage(EditS10nNameDialogState.Name),
        Errors.accessDenied
      )

    val tgUser = cb.from.toUser()
    userRepo.getOrCreate(tgUser)
      .transact(xa)
      .flatMap { user =>
        cb.data.flatTraverse { data =>
          val request = EditS10nNameCbData.fromString(data)
          s10nRepo.getById(request.subscriptionId)
            .transact(xa)
            .flatMap { s10nOpt =>
              s10nOpt.traverse { s10n =>
                checkUserAndGetMessage(s10n) match {
                  case Left(error) =>
                    Sync[F].pure(ReplyMessage(error))
                  case Right(reply) =>
                    val userWithNewState = user.copy(
                      dialogType = DialogType.EditS10nName.some,
                      editS10nNameDialogState = EditS10nNameDialogState.Name.some,
                      existingS10nDraft = s10n.some
                    )
                    userRepo.update(userWithNewState).transact(xa) *> reply
                }
              }
            }
        }
          .map(_.toRight(Errors.notFound))
      }
  }

  def saveName(user: User, text: Option[String]): F[ValidationResult[ReplyMessage]] =
    validateText(text)
      .andThen(name => validateNameLength(SubscriptionName(name)))
      .traverse(editS10nDialogFsmService.saveName(user, _))
}
