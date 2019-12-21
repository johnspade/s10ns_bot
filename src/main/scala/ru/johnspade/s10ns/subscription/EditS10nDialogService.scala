package ru.johnspade.s10ns.subscription

import cats.effect.Sync
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import ru.johnspade.s10ns.common.Errors
import ru.johnspade.s10ns.common.ValidatorNec.{ValidationResult, validateNameLength, validateText}
import ru.johnspade.s10ns.subscription.tags._
import ru.johnspade.s10ns.telegram.TelegramOps.TelegramUserOps
import ru.johnspade.s10ns.telegram.{DialogEngine, EditS10nName, ReplyMessage}
import ru.johnspade.s10ns.user.{EditS10nDialogFsmService, EditS10nNameDialog, EditS10nNameDialogState, User, UserRepository}
import telegramium.bots.CallbackQuery

class EditS10nDialogService[F[_] : Sync](
  private val userRepo: UserRepository,
  private val s10nRepo: SubscriptionRepository,
  private val editS10nDialogFsmService: EditS10nDialogFsmService[F],
  private val stateMessageService: StateMessageService[F],
  private val dialogEngine: DialogEngine[F]
)(private implicit val xa: Transactor[F]) {
  def onEditS10nNameCb(cb: CallbackQuery, data: EditS10nName): F[ReplyMessage] = {
    def saveAndReply(user: User, s10n: Subscription) = {
      val checkUserAndGetMessage = Either.cond(
        s10n.userId == user.id,
        stateMessageService.getMessage(EditS10nNameDialogState.Name),
        Errors.accessDenied
      )
      checkUserAndGetMessage match {
        case Left(error) => Sync[F].pure(ReplyMessage(error))
        case Right(reply) =>
          val dialog = EditS10nNameDialog(
            state = EditS10nNameDialogState.Name,
            draft = s10n
          )
          reply.flatMap(dialogEngine.startDialog(user, dialog, _))
      }
    }

    val tgUser = cb.from.toUser()
    for {
      user <- userRepo.getOrCreate(tgUser).transact(xa)
      s10nOpt <- s10nRepo.getById(data.subscriptionId).transact(xa)
      replyOpt <- s10nOpt.traverse(saveAndReply(user, _))
    } yield replyOpt.getOrElse(ReplyMessage(Errors.notFound))
  }

  def saveName(user: User, dialog: EditS10nNameDialog, text: Option[String]): F[ValidationResult[List[ReplyMessage]]] =
    validateText(text)
      .andThen(name => validateNameLength(SubscriptionName(name)))
      .traverse(editS10nDialogFsmService.saveName(user, dialog, _))
}
