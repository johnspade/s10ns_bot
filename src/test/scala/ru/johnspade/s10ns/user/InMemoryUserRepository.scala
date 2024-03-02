package ru.johnspade.s10ns.user

import scala.collection.concurrent.TrieMap

import cats.Id
import cats.syntax.option._

class InMemoryUserRepository extends UserRepository[Id] {
  val users: TrieMap[Long, User] = TrieMap.empty

  override def create(user: User): User = {
    users.put(user.id, user)
    user
  }

  override def getById(id: Long): Option[User] = users.get(id)

  override def update(user: User): Option[User] = {
    users.put(user.id, user)
    user.some
  }

  override def getOrCreate(user: User): User = users.getOrElseUpdate(user.id, user)

  override def createOrUpdate(user: User): User = {
    users.put(user.id, user)
    user
  }
}
