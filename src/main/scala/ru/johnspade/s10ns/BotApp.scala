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
import ru.johnspade.s10ns.bot.engine.DialogEngine
import ru.johnspade.s10ns.bot.{CbDataService, Config, MoneyService, StartController, StateMessageService}
import ru.johnspade.s10ns.calendar.{CalendarController, CalendarService}
import ru.johnspade.s10ns.exchangerates.{DoobieExchangeRatesRefreshTimestampRepository, DoobieExchangeRatesRepository, ExchangeRatesCache, ExchangeRatesJobService, ExchangeRatesRefreshTimestampRepository, ExchangeRatesRepository, ExchangeRatesService, FixerApiInterpreter}
import ru.johnspade.s10ns.settings.{SettingsController, SettingsService}
import ru.johnspade.s10ns.subscription.controller.{CreateS10nDialogController, EditS10nDialogController, SubscriptionListController}
import ru.johnspade.s10ns.subscription.repository.{DoobieSubscriptionRepository, SubscriptionRepository}
import ru.johnspade.s10ns.subscription.service.{CreateS10nDialogFsmService, CreateS10nDialogService, EditS10nDialogFsmService, EditS10nDialogService, S10nInfoService, S10nsListMessageService, SubscriptionListService}
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
      s10nListController: SubscriptionListController[F, D],
      createS10nDialogController: CreateS10nDialogController[F, D],
      editS10nDialogController: EditS10nDialogController[F, D],
      settingsController: SettingsController[F, D],
      calendarController: CalendarController[F],
      startController: StartController[F, D],
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
      val calendarService = new CalendarService[F]
      val stateMessageService = new StateMessageService[F](calendarService)

      for {
        implicit0(logger: Logger[F]) <- Slf4jLogger.create[F]
        exchangeRates <- transact(exchangeRatesRepo.get())
        exchangeRatesCache <- ExchangeRatesCache.create[F](exchangeRates)
        dialogEngine = new DialogEngine[F, D](userRepo)
        fixerApi = new FixerApiInterpreter[F](conf.fixer.token)
        exchangeRatesService = new ExchangeRatesService[F, D](
          fixerApi,
          exchangeRatesRepo,
          exchangeRatesRefreshTimestampRepo,
          exchangeRatesCache
        )
        moneyService = new MoneyService[F](exchangeRatesService)
        s10nInfoService = new S10nInfoService[F](moneyService)
        s10nsListService = new S10nsListMessageService[F](moneyService, s10nInfoService)
        createS10nDialogFsmService = new CreateS10nDialogFsmService[F, D](
          s10nRepo,
          userRepo,
          stateMessageService,
          dialogEngine,
          s10nsListService
        )
        cbDataService = new CbDataService[F]
        editS10nDialogFsmService = new EditS10nDialogFsmService[F, D](
          s10nsListService,
          stateMessageService,
          userRepo,
          s10nRepo,
          dialogEngine
        )
        editS10nDialogService = new EditS10nDialogService[F, D](
          s10nRepo,
          editS10nDialogFsmService,
          stateMessageService,
          dialogEngine
        )
        s10nCbService = new SubscriptionListService[F, D](s10nRepo, s10nsListService)
        createS10nDialogService = new CreateS10nDialogService[F, D](
          userRepo,
          createS10nDialogFsmService,
          stateMessageService,
          dialogEngine
        )
        settingsService = new SettingsService[F, D](dialogEngine, stateMessageService)
        exchangeRatesJobService = new ExchangeRatesJobService[F, D](exchangeRatesService, exchangeRatesRefreshTimestampRepo)
        s10nListController = new SubscriptionListController[F, D](s10nCbService)
        createS10nDialogController = new CreateS10nDialogController[F, D](createS10nDialogService)
        editS10nDialogController = new EditS10nDialogController[F, D](editS10nDialogService)
        settingsController = new SettingsController[F, D](settingsService)
        calendarController = new CalendarController[F](calendarService)
        startController = new StartController[F, D](dialogEngine)
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
