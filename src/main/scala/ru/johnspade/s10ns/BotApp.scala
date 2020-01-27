package ru.johnspade.s10ns

import cats.effect.{Async, Blocker, ConcurrentEffect, ContextShift, ExitCode, IO, IOApp, Sync, Timer}
import cats.implicits._
import cats.~>
import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.asynchttpclient.cats.AsyncHttpClientCatsBackend
import doobie.free.connection.ConnectionIO
import doobie.hikari.HikariTransactor
import doobie.implicits._
import doobie.util.ExecutionContexts
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.flywaydb.core.Flyway
import org.http4s.client.blaze.BlazeClientBuilder
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import pureconfig.module.catseffect._
import ru.johnspade.s10ns.bot.engine.DefaultDialogEngine
import ru.johnspade.s10ns.bot.{CbDataService, Config, MoneyService, StartController, StateMessageService}
import ru.johnspade.s10ns.calendar.{CalendarController, CalendarService}
import ru.johnspade.s10ns.exchangerates.{DoobieExchangeRatesRefreshTimestampRepository, DoobieExchangeRatesRepository, ExchangeRatesCache, DefaultExchangeRatesJobService, ExchangeRatesRefreshTimestampRepository, ExchangeRatesRepository, DefaultExchangeRatesService, FixerApiInterpreter}
import ru.johnspade.s10ns.settings.{DefaultSettingsService, SettingsController}
import ru.johnspade.s10ns.subscription.controller.{CreateS10nDialogController, EditS10nDialogController, SubscriptionListController}
import ru.johnspade.s10ns.subscription.repository.{DoobieSubscriptionRepository, SubscriptionRepository}
import ru.johnspade.s10ns.subscription.service.{DefaultCreateS10nDialogFsmService, DefaultCreateS10nDialogService, DefaultEditS10nDialogFsmService, DefaultEditS10nDialogService, S10nInfoService, S10nsListMessageService, DefaultSubscriptionListService}
import ru.johnspade.s10ns.user.{DoobieUserRepository, UserRepository}
import telegramium.bots.client.ApiHttp4sImp

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

object BotApp extends IOApp {
  private type F[A] = IO[A]
  private type D[A] = ConnectionIO[A]

  private val userRepo: UserRepository[D] = new DoobieUserRepository
  private val s10nRepo: SubscriptionRepository[D] = new DoobieSubscriptionRepository
  private val exchangeRatesRepo: ExchangeRatesRepository[D] = new DoobieExchangeRatesRepository
  private val exchangeRatesRefreshTimestampRepo: ExchangeRatesRefreshTimestampRepository[D] =
    new DoobieExchangeRatesRefreshTimestampRepository

  override def run(args: List[String]): IO[ExitCode] = {
    def createTransactor[F[_] : Async : ContextShift](connectionUrl: String) =
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

    def startBot[F[_] : Sync : ConcurrentEffect : ContextShift : Logger : Timer](
      token: String,
      s10nListController: SubscriptionListController[F],
      createS10nDialogController: CreateS10nDialogController[F],
      editS10nDialogController: EditS10nDialogController[F],
      settingsController: SettingsController[F],
      calendarController: CalendarController[F],
      startController: StartController[F],
      cbDataService: CbDataService[F]
    )(implicit transact: D ~> F) =
      BlazeClientBuilder[F](ExecutionContext.global).resource.use { httpClient =>
        val http = org.http4s.client.middleware.Logger(logBody = true, logHeaders = true)(httpClient)
        val api = new ApiHttp4sImp(http, baseUrl = s"https://api.telegram.org/bot$token")
        val bot = new SubscriptionsBot[F, D](
          api,
          userRepo,
          s10nListController,
          createS10nDialogController,
          editS10nDialogController,
          calendarController,
          settingsController,
          startController,
          cbDataService
        )
        bot.start().handleErrorWith(e => Logger[F].error(e)(e.getMessage))
      }

    def init[F[_] : ConcurrentEffect : ContextShift : Timer](conf: Config, transact: D ~> F) = {
      implicit val xa: D ~> F = transact
      implicit val sttpBackend: SttpBackend[F, Nothing] = AsyncHttpClientCatsBackend[F]()
      val calendarService = new CalendarService
      val stateMessageService = new StateMessageService[F](calendarService)

      for {
        implicit0(logger: Logger[F]) <- Slf4jLogger.create[F]
        exchangeRates <- transact(exchangeRatesRepo.get())
        exchangeRatesCache <- ExchangeRatesCache.create[F](exchangeRates)
        dialogEngine = new DefaultDialogEngine[F, D](userRepo)
        fixerApi = new FixerApiInterpreter[F](conf.fixer.token)
        exchangeRatesService = new DefaultExchangeRatesService[F, D](
          fixerApi,
          exchangeRatesRepo,
          exchangeRatesRefreshTimestampRepo,
          exchangeRatesCache
        )
        moneyService = new MoneyService[F](exchangeRatesService)
        s10nInfoService = new S10nInfoService[F](moneyService)
        s10nsListService = new S10nsListMessageService[F](moneyService, s10nInfoService)
        createS10nDialogFsmService = new DefaultCreateS10nDialogFsmService[F, D](
          s10nRepo,
          userRepo,
          stateMessageService,
          dialogEngine,
          s10nsListService
        )
        cbDataService = new CbDataService[F]
        editS10nDialogFsmService = new DefaultEditS10nDialogFsmService[F, D](
          s10nsListService,
          stateMessageService,
          userRepo,
          s10nRepo,
          dialogEngine
        )
        editS10nDialogService = new DefaultEditS10nDialogService[F, D](
          s10nRepo,
          editS10nDialogFsmService,
          stateMessageService,
          dialogEngine
        )
        s10nCbService = new DefaultSubscriptionListService[F, D](s10nRepo, s10nsListService)
        createS10nDialogService = new DefaultCreateS10nDialogService[F, D](
          userRepo,
          createS10nDialogFsmService,
          stateMessageService,
          dialogEngine
        )
        settingsService = new DefaultSettingsService[F](dialogEngine, stateMessageService)
        exchangeRatesJobService = new DefaultExchangeRatesJobService[F, D](exchangeRatesService, exchangeRatesRefreshTimestampRepo)
        s10nListController = new SubscriptionListController[F](s10nCbService)
        createS10nDialogController = new CreateS10nDialogController[F](createS10nDialogService)
        editS10nDialogController = new EditS10nDialogController[F](editS10nDialogService)
        settingsController = new SettingsController[F](settingsService)
        calendarController = new CalendarController[F](calendarService)
        startController = new StartController[F](dialogEngine)
        _ <- exchangeRatesJobService.startExchangeRatesJob()
        _ <- startBot[F](
          conf.telegram.token,
          s10nListController,
          createS10nDialogController,
          editS10nDialogController,
          settingsController,
          calendarController,
          startController,
          cbDataService
        )
      } yield ()
    }

    ConfigSource.file("config.properties").loadF[IO, Config].flatMap { conf =>
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
            init[IO](conf, transact).map(_ => ExitCode.Success)
        }
    }
  }
}
