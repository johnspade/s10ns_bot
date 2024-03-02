package ru.johnspade.s10ns.subscription.service.impl

import cats.Monad
import cats.implicits._
import cats.~>

import telegramium.bots.CallbackQuery

import ru.johnspade.s10ns.bot.EditS10nDialog
import ru.johnspade.s10ns.bot.Errors
import ru.johnspade.s10ns.bot.engine.DialogState
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.engine.StateEvent
import ru.johnspade.s10ns.bot.engine.StateMessageService
import ru.johnspade.s10ns.bot.engine.TelegramOps.singleTextMessage
import ru.johnspade.s10ns.bot.engine.TransactionalDialogEngine
import ru.johnspade.s10ns.subscription.Subscription
import ru.johnspade.s10ns.subscription.repository.SubscriptionRepository
import ru.johnspade.s10ns.subscription.service.S10nsListMessageService
import ru.johnspade.s10ns.user.User
import ru.johnspade.s10ns.user.UserRepository

abstract class EditS10nDialogService[F[_]: Monad, D[_]: Monad, S <: DialogState](
    private val s10nsListMessageService: S10nsListMessageService[F],
    private val stateMessageService: StateMessageService[F, S],
    private val userRepo: UserRepository[D],
    private val s10nRepo: SubscriptionRepository[D],
    private val dialogEngine: TransactionalDialogEngine[F, D]
)(private implicit val transact: D ~> F) {
  def transition(user: User, dialog: EditS10nDialog.Aux[S])(event: dialog.E): F[List[ReplyMessage]] = {
    def createReply(dialog: EditS10nDialog.Aux[S]) =
      stateMessageService.createReplyMessage(dialog.state).map(List(_))

    val updatedDialog = dialog.transition(event)
    if (updatedDialog.state == dialog.finished)
      onFinish(user, dialog.draft, dialog.finished.message)
    else {
      val userWithUpdatedDialog = user.copy(dialog = updatedDialog.some)
      transact(userRepo.createOrUpdate(userWithUpdatedDialog)) *> createReply(updatedDialog)
    }
  }

  private def onFinish(user: User, draft: Subscription, message: String) = {
    val replyOpt = transact {
      s10nRepo
        .update(draft)
        .flatMap(_.traverse { s10n =>
          dialogEngine
            .reset(user, message)
            .map((_, s10n))
        })
    }
    for {
      reply <- replyOpt
      replies <- reply.traverse { p =>
        s10nsListMessageService.createSubscriptionMessage(user.defaultCurrency, p._2, 0).map(List(p._1, _))
      }
    } yield replies.getOrElse(singleTextMessage(Errors.NotFound))
  }

  protected def onEditS10nDialogCb(
      user: User,
      s10nId: Long,
      state: S,
      createDialog: Subscription => EditS10nDialog
  ): F[List[ReplyMessage]] = {
    def saveAndReply(s10n: Subscription) = {
      val checkUserAndGetState = Either.cond(
        s10n.userId == user.id,
        state,
        Errors.AccessDenied
      )
      checkUserAndGetState match {
        case Left(error) => Monad[F].pure(List(ReplyMessage(error)))
        case Right(state) =>
          val dialog = createDialog(s10n)
          stateMessageService.createReplyMessage(state).flatMap(dialogEngine.startDialog(user, dialog, _))
      }
    }

    for {
      s10nOpt  <- transact(s10nRepo.getById(s10nId))
      replyOpt <- s10nOpt.traverse(saveAndReply)
    } yield replyOpt.getOrElse(List(ReplyMessage(Errors.NotFound)))
  }
}
