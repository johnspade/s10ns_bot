package ru.johnspade.s10ns.user

import cats.effect.Sync

import doobie.free.connection.ConnectionIO

final class UserModule[D[_]] private (
  val userRepository: UserRepository[D]
)

object UserModule {
  def make[F[_]: Sync](): F[UserModule[ConnectionIO]] = Sync[F].delay {
    new UserModule[ConnectionIO](
      userRepository = new DoobieUserRepository
    )
  }
}
