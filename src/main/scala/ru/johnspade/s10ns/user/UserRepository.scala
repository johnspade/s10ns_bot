package ru.johnspade.s10ns.user

import ru.johnspade.s10ns.user.tags._

trait UserRepository[D[_]] {
  def create(user: User): D[User]

  def getById(id: UserId): D[Option[User]]

  def update(user: User): D[Option[User]]

  def getOrCreate(user: User): D[User]

  def createOrUpdate(user: User): D[User]
}
