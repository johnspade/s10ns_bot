package ru.johnspade.s10ns.help

import cats.effect.Sync
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import ru.johnspade.s10ns.user.{User, UserRepository}

class StartService[F[_] : Sync](
  private val userRepo: UserRepository,
  private val xa: Transactor[F]
) {
  def reset(user: User): F[Unit] =
    if (Seq(user.dialogType, user.subscriptionDialogState, user.settingsDialogState, user.subscriptionDraft).flatten.nonEmpty) {
      val resetUser = user.copy(
        dialogType = None,
        subscriptionDialogState = None,
        settingsDialogState = None,
        subscriptionDraft = None
      )
      userRepo.update(resetUser).transact(xa).void
    }
    else
      Sync[F].unit
}
