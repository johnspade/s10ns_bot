package ru.johnspade.s10ns.user

import cats.Id
import ru.johnspade.s10ns.user.tags.UserId
import cats.syntax.option._

import scala.collection.concurrent.TrieMap

class InMemoryUserRepository extends UserRepository[Id] {
  val users: TrieMap[UserId, User] = TrieMap.empty

  override def create(user: User): User = {
    users.put(user.id, user).get
    user
  }

  override def getById(id: UserId): Option[User] = users.get(id)

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
