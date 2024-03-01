package ru.johnspade.s10ns.user

import cats.data.OptionT

import doobie.free.connection
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.util.query.Query0
import doobie.util.update.Update0

import ru.johnspade.s10ns.user.DoobieUserMeta._
import ru.johnspade.s10ns.user.DoobieUserRepository.UserSql
import ru.johnspade.s10ns.user.tags._

class DoobieUserRepository extends UserRepository[ConnectionIO] {
  override def create(user: User): ConnectionIO[User] =
    UserSql
      .create(user)
      .run
      .map(_ => user)

  override def getById(id: UserId): ConnectionIO[Option[User]] =
    UserSql
      .get(id)
      .option

  override def update(user: User): ConnectionIO[Option[User]] = {
    for {
      oldUser <- UserSql.get(user.id).option
      newUser = oldUser.map(_ => user)
      _ <- newUser.fold(connection.unit)(UserSql.update(_).run.map(_ => ()))
    } yield newUser
  }

  def getOrCreate(user: User): ConnectionIO[User] = OptionT(getById(user.id)).getOrElseF(create(user))

  def createOrUpdate(user: User): ConnectionIO[User] = OptionT(update(user)).getOrElseF(create(user))
}

object DoobieUserRepository {
  object UserSql {
    def create(user: User): Update0 =
      sql"""
        insert into users (id, first_name, chat_id, default_currency, dialog)
        values (${user.id}, ${user.firstName}, ${user.chatId}, ${user.defaultCurrency}, ${user.dialog})
      """.update

    def get(id: UserId): Query0[User] = sql"""
        select id, first_name, chat_id, default_currency, dialog
        from users
        where id = $id
      """.query[User]

    def update(user: User): Update0 =
      sql"""
        update users set
        first_name = ${user.firstName},
        chat_id = ${user.chatId},
        default_currency = ${user.defaultCurrency},
        dialog = ${user.dialog}
        where id = ${user.id}
      """.update
  }
}
