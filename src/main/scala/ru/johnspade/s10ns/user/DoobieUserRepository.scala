package ru.johnspade.s10ns.user

import java.time.temporal.ChronoUnit

import cats.data.OptionT
import cats.implicits._
import doobie.free.connection
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.util.Meta
import doobie.util.log.LogHandler
import doobie.util.query.Query0
import doobie.util.update.Update0
import org.joda.money.CurrencyUnit
import org.postgresql.util.PGobject
import ru.johnspade.s10ns.subscription._
import ru.johnspade.s10ns.user.DoobieUserRepository.UserSql

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
      _ <- newUser.fold(connection.unit)(UserSql.update(_).run.void)
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
        subscription_dialog_state, settings_dialog_state, subscription_draft)
        values (${user.id.value}, ${user.firstName.value}, ${user.lastName.map(_.value)}, ${user.username.map(_.value)},
        ${user.chatId.map(_.value)}, ${user.defaultCurrency}, ${user.dialogType}, ${user.subscriptionDialogState},
        ${user.settingsDialogState}, ${user.subscriptionDraft})
      """.update

    def get(id: UserId): Query0[User] = sql"""
        select id, first_name, last_name, username, chat_id, default_currency, dialog_type, subscription_dialog_state,
        settings_dialog_state, subscription_draft
        from users
        where id = ${id.value}
      """.query[User]

    def update(user: User): Update0 =
      sql"""
        update users set
        first_name = ${user.firstName.value},
        last_name = ${user.lastName.map(_.value)},
        username = ${user.username.map(_.value)},
        chat_id = ${user.chatId.map(_.value)},
        default_currency = ${user.defaultCurrency},
        dialog_type = ${user.dialogType},
        subscription_dialog_state = ${user.subscriptionDialogState},
        settings_dialog_state = ${user.settingsDialogState},
        subscription_draft = ${user.subscriptionDraft}
        where id = ${user.id.value}
      """.update
  }

  import io.circe.generic.auto._
  import io.circe.generic.extras.defaults._
  import io.circe.generic.extras.semiauto._
  import io.circe.parser._
  import io.circe.syntax._
  import io.circe.{Decoder, Encoder}

  private implicit val SubscriptionEncoder: Encoder[SubscriptionDraft] = deriveEncoder
  private implicit val SubscriptionDecoder: Decoder[SubscriptionDraft] = deriveDecoder

  private implicit val SubscriptionIdEncoder: Encoder[SubscriptionId] = deriveUnwrappedEncoder
  private implicit val SubscriptionIdDecoder: Decoder[SubscriptionId] = deriveUnwrappedDecoder

  private implicit val SubscriptionNameEncoder: Encoder[SubscriptionName] = deriveUnwrappedEncoder
  private implicit val SubscriptionNameDecoder: Decoder[SubscriptionName] = deriveUnwrappedDecoder

  private implicit val SubscriptionAmountEncoder: Encoder[SubscriptionAmount] = deriveUnwrappedEncoder
  private implicit val SubscriptionAmountDecoder: Decoder[SubscriptionAmount] = deriveUnwrappedDecoder

  private implicit val CurrencyUnitEncoder: Encoder[CurrencyUnit] = Encoder.encodeString.contramap(_.getCode)
  private implicit val CurrencyUnitDecoder: Decoder[CurrencyUnit] = Decoder.decodeString.emap { str =>
    Either.catchNonFatal(CurrencyUnit.of(str)).leftMap(_ => "CurrencyUnit")
  }

  private implicit val SubscriptionDescriptionEncoder: Encoder[SubscriptionDescription] = deriveUnwrappedEncoder
  private implicit val SubscriptionDescriptionDecoder: Decoder[SubscriptionDescription] = deriveUnwrappedDecoder

  private implicit val BillingPeriodDurationEncoder: Encoder[BillingPeriodDuration] = deriveUnwrappedEncoder
  private implicit val BillingPeriodDurationDecoder: Decoder[BillingPeriodDuration] = deriveUnwrappedDecoder

  private implicit val ChronoUnitEncoder: Encoder[ChronoUnit] = Encoder.encodeString.contramap(_.name())
  private implicit val ChronoUnitDecoder: Decoder[ChronoUnit] = Decoder.decodeString.emap { str =>
    Either.catchNonFatal(ChronoUnit.valueOf(str)).leftMap(_ => "BillingPeriodUnit")
  }

  private implicit val BillingPeriodUnitEncoder: Encoder[BillingPeriodUnit] = deriveUnwrappedEncoder
  private implicit val BillingPeriodUnitDecoder: Decoder[BillingPeriodUnit] = deriveUnwrappedDecoder

  private implicit val OneTimeSubscriptionEncoder: Encoder[OneTimeSubscription] = deriveUnwrappedEncoder
  private implicit val OneTimeSubscriptionDecoder: Decoder[OneTimeSubscription] = deriveUnwrappedDecoder

  private implicit val FirstPaymentDateEncoder: Encoder[FirstPaymentDate] = deriveUnwrappedEncoder
  private implicit val FirstPaymentDateDecoder: Decoder[FirstPaymentDate] = deriveUnwrappedDecoder

  private implicit val subscriptionJsonMeta: doobie.Meta[SubscriptionDraft] =
    doobie.Meta.Advanced
      .other[PGobject]("jsonb")
      .imap[SubscriptionDraft](jsonStr => decode[SubscriptionDraft](jsonStr.getValue).leftMap(err => throw err).merge)(
        subscription => {
          val o = new PGobject
          o.setType("jsonb")
          o.setValue(subscription.asJson.noSpaces)
          o
        }
      )

  private implicit val currencyUnitMeta: Meta[CurrencyUnit] = Meta[String].timap(CurrencyUnit.of)(_.getCode)
}


