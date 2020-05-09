package ru.johnspade.s10ns

import cats.effect.{Async, Blocker, ConcurrentEffect, ContextShift, ExitCode, IO, IOApp, Timer}
import cats.implicits._
import cats.~>
import doobie.free.connection.ConnectionIO
import doobie.hikari.HikariTransactor
import doobie.implicits._
import doobie.util.ExecutionContexts
import org.flywaydb.core.Flyway
import org.http4s.client.blaze.BlazeClientBuilder
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import pureconfig.module.catseffect._
import ru.johnspade.s10ns.bot.{BotModule, Config}
import ru.johnspade.s10ns.calendar.CalendarModule
import ru.johnspade.s10ns.exchangerates.ExchangeRatesModule
import ru.johnspade.s10ns.settings.SettingsModule
import ru.johnspade.s10ns.subscription.SubscriptionModule
import ru.johnspade.s10ns.user.UserModule
import telegramium.bots.client.ApiHttp4sImp
import tofu.logging._

import scala.concurrent.ExecutionContext

object BotApp extends IOApp {
  private type F[A] = IO[A]
  private type D[A] = ConnectionIO[A]

  override def run(args: List[String]): IO[ExitCode] = {
    def createTransactor[F[_]: Async: ContextShift](connectionUrl: String) =
      for {
        ce <- ExecutionContexts.fixedThreadPool[F](32)
        te <- ExecutionContexts.cachedThreadPool[F]
        xa <- HikariTransactor.newHikariTransactor[F](
          "org.postgresql.Driver",
          s"jdbc:postgresql://$connectionUrl",
          null,
          null,
          ce,
          Blocker.liftExecutionContext(te)
        )
      } yield xa

    def init[F[_]: ConcurrentEffect: ContextShift: Timer](conf: Config, transact: D ~> F, blocker: Blocker) = {
      implicit val logs: Logs[F, F] = Logs.sync[F, F]
      implicit val xa: D ~> F = transact

      for {
        calendarModule <- CalendarModule.make[F]()
        userModule <- UserModule.make[F]()
        exchangeRatesModule <- ExchangeRatesModule.make[F](conf.fixer.token)
        botModule <- BotModule.make[F](userModule, exchangeRatesModule)
        settingsModule <- SettingsModule.make[F](botModule)
        subscriptionModule <- SubscriptionModule.make[F](userModule, botModule, calendarModule)
        _ <- exchangeRatesModule.exchangeRatesJobService.startExchangeRatesJob()
        _ <- BlazeClientBuilder[F](ExecutionContext.global).resource.use { httpClient =>
          val api = new ApiHttp4sImp(httpClient, baseUrl = s"https://api.telegram.org/bot${conf.telegram.token}", blocker)
          for {
            bot <- SubscriptionsBot[F, D](
              api,
              userModule.userRepository,
              subscriptionModule.subscriptionListController,
              subscriptionModule.createS10nDialogController,
              subscriptionModule.editS10nDialogController,
              calendarModule.calendarController,
              settingsModule.settingsController,
              botModule.startController,
              botModule.cbDataService
            )
            logger <- logs.forService[BotApp.type]
            _ <- bot.start().handleErrorWith(e => logger.errorCause(e.getMessage, e))
          } yield ()
        }
      } yield ()
    }

    Blocker[IO].use { blocker =>
      loadF[IO, Config](ConfigSource.file("config.properties"), blocker).flatMap { conf =>
        createTransactor[IO](conf.app.db)
          .use { xa =>
            val transact = new ~>[ConnectionIO, IO] {
              override def apply[A](fa: ConnectionIO[A]): IO[A] = fa.transact(xa)
            }
            xa.configure { dataSource =>
              IO {
                val flyWay = Flyway.configure().dataSource(dataSource).load()
                flyWay.migrate()
                ()
              }
            } *>
              init[IO](conf, transact, blocker).map(_ => ExitCode.Success)
          }
      }
    }
  }
}
