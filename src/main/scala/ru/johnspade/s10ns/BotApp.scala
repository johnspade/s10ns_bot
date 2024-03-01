package ru.johnspade.s10ns

import cats.Parallel
import cats.effect.Async
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Temporal
import cats.implicits._
import cats.~>

import doobie.free.connection.ConnectionIO
import doobie.hikari.HikariTransactor
import doobie.implicits._
import doobie.util.ExecutionContexts
import org.flywaydb.core.Flyway
import org.http4s.blaze.client.BlazeClientBuilder
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import telegramium.bots.high._
import tofu.logging._

import ru.johnspade.s10ns.bot.BotModule
import ru.johnspade.s10ns.bot.Config
import ru.johnspade.s10ns.bot.DbConfig
import ru.johnspade.s10ns.calendar.CalendarModule
import ru.johnspade.s10ns.exchangerates.ExchangeRatesModule
import ru.johnspade.s10ns.notifications.NotificationsModule
import ru.johnspade.s10ns.settings.SettingsModule
import ru.johnspade.s10ns.subscription.SubscriptionModule
import ru.johnspade.s10ns.user.UserModule

object BotApp extends IOApp {
  private type F[A] = IO[A]
  private type D[A] = ConnectionIO[A]

  override def run(args: List[String]): IO[ExitCode] = {
    def createTransactor[F[_]: Async](dbConfig: DbConfig) =
      for {
        te <- ExecutionContexts.cachedThreadPool[F]
        xa <- HikariTransactor.newHikariTransactor[F](
          dbConfig.driver,
          dbConfig.url,
          dbConfig.user,
          dbConfig.password,
          te
        )
      } yield xa

    def init[F[_]: Temporal: Parallel: Async](conf: Config, transact: D ~> F) = {
      implicit val logs: Logs[F, F] = Logs.sync[F, F]
      implicit val xa: D ~> F       = transact

      BlazeClientBuilder[F].resource.use { httpClient =>
        implicit val api: Api[F] =
          BotApi(httpClient, s"https://api.telegram.org/bot${conf.bot.token}")
        for {
          calendarModule      <- CalendarModule.make[F]
          userModule          <- UserModule.make[F]()
          exchangeRatesModule <- ExchangeRatesModule.make[F](conf.fixer.token)
          botModule           <- BotModule.make[F](userModule, exchangeRatesModule)
          settingsModule      <- SettingsModule.make[F](botModule)
          subscriptionModule  <- SubscriptionModule.make[F](userModule, botModule, calendarModule)
          notificationsModule <- NotificationsModule.make[F](subscriptionModule)
          _                   <- exchangeRatesModule.exchangeRatesJobService.startExchangeRatesJob()
          _                   <- notificationsModule.prepareNotificationsJobService.startPrepareNotificationsJob()
          _                   <- notificationsModule.notificationsJobService.startNotificationsJob()
          bot <- SubscriptionsBot[F, D](
            conf.bot,
            userModule.userRepository,
            subscriptionModule.subscriptionListController,
            subscriptionModule.editS10nDialogController,
            calendarModule.calendarController,
            settingsModule.settingsController,
            botModule.startController,
            botModule.ignoreController,
            botModule.cbDataService,
            botModule.userMiddleware
          )
          _ <- bot
            .start(
              port = conf.bot.port,
              host = "0.0.0.0"
            )
            .use(_ => Async[F].async_[Unit](_ => ()))
        } yield ()
      }
    }

    IO(ConfigSource.default.loadOrThrow[Config]).flatMap { conf =>
      createTransactor[IO](conf.db)
        .use { xa =>
          val transact = new ~>[ConnectionIO, IO] {
            override def apply[A](fa: ConnectionIO[A]): IO[A] = fa.transact(xa)
          }
          xa.configure { dataSource =>
            IO {
              val flyway = Flyway.configure().dataSource(dataSource).load()
              flyway.migrate()
              ()
            }
          } *>
            init[IO](conf, transact).map(_ => ExitCode.Success)
        }
    }
  }
}
