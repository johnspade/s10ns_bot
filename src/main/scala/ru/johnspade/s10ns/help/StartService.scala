package ru.johnspade.s10ns.help

import cats.effect.Sync
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import ru.johnspade.s10ns.user.{User, UserRepository}

class StartService[F[_] : Sync](
  private val userRepo: UserRepository
)(private implicit val xa: Transactor[F]) {
  def reset(user: User): F[Unit] =
    if (user.dialog.nonEmpty) {
      val resetUser = user.copy(dialog = None)
      userRepo.update(resetUser).transact(xa).void
    }
    else
      Sync[F].unit
}
