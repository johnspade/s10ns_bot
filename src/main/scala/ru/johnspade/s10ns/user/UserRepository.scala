package ru.johnspade.s10ns.user

import doobie.free.connection.ConnectionIO
import ru.johnspade.s10ns.user.tags._

trait UserRepository {
  def create(user: User): ConnectionIO[User]

  def getById(id: UserId): ConnectionIO[Option[User]]

  def update(user: User): ConnectionIO[Option[User]]

  def getOrCreate(user: User): ConnectionIO[User]

  def createOrUpdate(user: User): ConnectionIO[User]
}
