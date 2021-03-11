package ru.johnspade.s10ns

import cats.effect.{Async, Blocker, ConcurrentEffect, ContextShift, ExitCode, IO, IOApp, Timer}
import cats.implicits._
import cats.{Parallel, ~>}
import doobie.free.connection.ConnectionIO
import doobie.hikari.HikariTransactor
import doobie.implicits._
import doobie.util.ExecutionContexts
import org.flywaydb.core.Flyway
import org.http4s.client.blaze.BlazeClientBuilder
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import ru.johnspade.s10ns.bot.{BotModule, Config, DbConfig}
import ru.johnspade.s10ns.calendar.CalendarModule
import ru.johnspade.s10ns.exchangerates.ExchangeRatesModule
import ru.johnspade.s10ns.notifications.NotificationsModule
import ru.johnspade.s10ns.settings.SettingsModule
import ru.johnspade.s10ns.subscription.SubscriptionModule
import ru.johnspade.s10ns.user.UserModule
import telegramium.bots.high._
import tofu.logging._

import scala.concurrent.ExecutionContext

object BotApp extends IOApp {
  private type F[A] = IO[A]
  private type D[A] = ConnectionIO[A]

  override def run(args: List[String]): IO[ExitCode] = {
    def createTransactor[F[_]: Async: ContextShift](dbConfig: DbConfig) =
      for {
        ce <- ExecutionContexts.fixedThreadPool[F](32)
        te <- ExecutionContexts.cachedThreadPool[F]
        xa <- HikariTransactor.newHikariTransactor[F](
          dbConfig.driver,
          dbConfig.url,
          dbConfig.user,
          dbConfig.password,
          ce,
          Blocker.liftExecutionContext(te)
        )
      } yield xa

    def init[F[_]: ConcurrentEffect: ContextShift: Timer: Parallel](conf: Config, transact: D ~> F, blocker: Blocker) = {
      implicit val logs: Logs[F, F] = Logs.sync[F, F]
      implicit val xa: D ~> F = transact

      BlazeClientBuilder[F](ExecutionContext.global).resource.use { httpClient =>
        implicit val api: Api[F] =
          BotApi(httpClient, s"https://api.telegram.org/bot${conf.bot.token}", blocker)
        for {
          calendarModule <- CalendarModule.make[F]
          userModule <- UserModule.make[F]()
          exchangeRatesModule <- ExchangeRatesModule.make[F](conf.fixer.token)
          botModule <- BotModule.make[F](userModule, exchangeRatesModule)
          settingsModule <- SettingsModule.make[F](botModule)
          subscriptionModule <- SubscriptionModule.make[F](userModule, botModule, calendarModule)
          notificationsModule <- NotificationsModule.make[F](subscriptionModule)
          _ <- exchangeRatesModule.exchangeRatesJobService.startExchangeRatesJob()
          _ <- notificationsModule.prepareNotificationsJobService.startPrepareNotificationsJob()
          _ <- notificationsModule.notificationsJobService.startNotificationsJob()
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
          _ <- bot.start().use(_ => Async[F].async[Unit](_ => ()))
        } yield ()
      }
    }

    Blocker[IO].use { blocker =>
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
              init[IO](conf, transact, blocker).map(_ => ExitCode.Success)
          }
      }
    }
  }
}
