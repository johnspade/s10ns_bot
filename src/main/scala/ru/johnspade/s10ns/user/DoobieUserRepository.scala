package ru.johnspade.s10ns.user

import java.time.temporal.ChronoUnit

import cats.data.OptionT
import doobie.free.connection
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.util.Meta
import doobie.util.log.LogHandler
import doobie.util.query.Query0
import doobie.util.update.Update0
import org.joda.money.{CurrencyUnit, Money}
import org.postgresql.util.PGobject
import ru.johnspade.s10ns.subscription._
import ru.johnspade.s10ns.user.DoobieUserRepository.UserSql
import ru.johnspade.s10ns.user.tags._

class DoobieUserRepository extends UserRepository {
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
  private object UserSql {
    private implicit val han: LogHandler = LogHandler.jdkLogHandler

    def create(user: User): Update0 =
      sql"""
        insert into users (id, first_name, last_name, username, chat_id, default_currency, dialog_type,
        subscription_dialog_state, settings_dialog_state, edit_s10n_name_dialog_state, subscription_draft,
        existing_subscription_draft)
        values (${user.id}, ${user.firstName}, ${user.lastName}, ${user.username},
        ${user.chatId}, ${user.defaultCurrency}, ${user.dialogType}, ${user.subscriptionDialogState},
        ${user.settingsDialogState}, ${user.editS10nNameDialogState}, ${user.subscriptionDraft}, ${user.existingS10nDraft})
      """.update

    def get(id: UserId): Query0[User] = sql"""
        select id, first_name, last_name, username, chat_id, default_currency, dialog_type, subscription_dialog_state,
        settings_dialog_state, edit_s10n_name_dialog_state, subscription_draft, existing_subscription_draft
        from users
        where id = $id
      """.query[User]

    def update(user: User): Update0 =
      sql"""
        update users set
        first_name = ${user.firstName},
        last_name = ${user.lastName},
        username = ${user.username},
        chat_id = ${user.chatId},
        default_currency = ${user.defaultCurrency},
        dialog_type = ${user.dialogType},
        subscription_dialog_state = ${user.subscriptionDialogState},
        settings_dialog_state = ${user.settingsDialogState},
        edit_s10n_name_dialog_state = ${user.editS10nNameDialogState},
        subscription_draft = ${user.subscriptionDraft},
        existing_subscription_draft = ${user.existingS10nDraft}
        where id = ${user.id}
      """.update
  }

  import cats.syntax.either._
  import io.circe.generic.auto._
  import io.circe.generic.extras.defaults._
  import io.circe.parser._
  import io.circe.syntax._
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

  private implicit val subscriptionJsonMeta: Meta[SubscriptionDraft] =
    Meta.Advanced
      .other[PGobject]("jsonb")
      .imap[SubscriptionDraft](jsonStr => decode[SubscriptionDraft](jsonStr.getValue).leftMap(err => throw err).merge)(
        subscription => {
          val o = new PGobject
          o.setType("jsonb")
          o.setValue(subscription.asJson.noSpaces)
          o
        }
      )

  private implicit val existingS10nJsonMeta: Meta[Subscription] =
    Meta.Advanced
      .other[PGobject]("jsonb")
      .imap[Subscription](jsonStr => decode[Subscription](jsonStr.getValue).leftMap(err => throw err).merge)(
        s10n => {
          val o = new PGobject
          o.setType("jsonb")
          o.setValue(s10n.asJson.noSpaces)
          o
        }
      )

  private implicit val currencyUnitMeta: Meta[CurrencyUnit] = Meta[String].timap(CurrencyUnit.of)(_.getCode)
}


