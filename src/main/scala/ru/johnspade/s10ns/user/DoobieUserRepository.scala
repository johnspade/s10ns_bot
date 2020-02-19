package ru.johnspade.s10ns.user

import java.time.temporal.ChronoUnit

import cats.data.OptionT
import doobie.free.connection
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.util.Meta
import doobie.util.query.Query0
import doobie.util.update.Update0
import org.joda.money.{CurrencyUnit, Money}
import org.postgresql.util.PGobject
import ru.johnspade.s10ns.bot.Dialog
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

  import cats.syntax.either._
  import io.circe.generic.extras.Configuration
  import io.circe.generic.extras.auto._
  import io.circe.parser._
  import io.circe.syntax._

  private implicit val jsonConfig: Configuration = Configuration.default.withDiscriminator("discriminator")

  private implicit val dialogJsonMeta: Meta[Dialog] =
    Meta.Advanced
      .other[PGobject]("jsonb")
      .imap[Dialog](jsonStr => decode[Dialog](jsonStr.getValue).leftMap(err => throw err).merge)(
        dialog => {
          val o = new PGobject
          o.setType("jsonb")
          o.setValue(dialog.asJson.noSpaces)
          o
        }
      )

  import io.circe.generic.auto._
  import io.circe.{Decoder, Encoder}

  private implicit val CurrencyUnitEncoder: Encoder[CurrencyUnit] = Encoder.encodeString.contramap(_.getCode)
  private implicit val CurrencyUnitDecoder: Decoder[CurrencyUnit] = Decoder.decodeString.emap { str =>
    Either.catchNonFatal(CurrencyUnit.of(str)).leftMap(_ => "CurrencyUnit")
  }

  private implicit val ChronoUnitEncoder: Encoder[ChronoUnit] = Encoder.encodeString.contramap(_.name())
  private implicit val ChronoUnitDecoder: Decoder[ChronoUnit] = Decoder.decodeString.emap { str =>
    Either.catchNonFatal(ChronoUnit.valueOf(str)).leftMap(_ => "BillingPeriodUnit")
  }

  private implicit val MoneyEncoder: Encoder[Money] = Encoder.encodeString.contramap(_.toString)
  private implicit val MoneyDecoder: Decoder[Money] = Decoder.decodeString.emap { s =>
    Either.catchNonFatal(Money.parse(s)).leftMap(_ => "Money")
  }

  private implicit val currencyUnitMeta: Meta[CurrencyUnit] = Meta[String].timap(CurrencyUnit.of)(_.getCode)
}
