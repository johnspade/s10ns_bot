package ru.johnspade.s10ns.user

trait UserRepository[D[_]] {
  def create(user: User): D[User]

  def getById(id: Long): D[Option[User]]

  def update(user: User): D[Option[User]]

  def getOrCreate(user: User): D[User]

  def createOrUpdate(user: User): D[User]
}
