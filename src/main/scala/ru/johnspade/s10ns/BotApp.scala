package ru.johnspade.s10ns

import cats.effect.{Async, Blocker, ConcurrentEffect, ContextShift, ExitCode, IO, IOApp, Sync, Timer}
import cats.implicits._
import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.asynchttpclient.cats.AsyncHttpClientCatsBackend
import doobie.hikari.HikariTransactor
import doobie.implicits._
import doobie.util.ExecutionContexts
import doobie.util.transactor.Transactor
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.flywaydb.core.Flyway
import org.http4s.client.blaze.BlazeClientBuilder
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import pureconfig.module.catseffect._
import ru.johnspade.s10ns.calendar.{CalendarController, CalendarService}
import ru.johnspade.s10ns.common.Config
import ru.johnspade.s10ns.exchangerates.{ExchangeRatesCache, ExchangeRatesJobService, ExchangeRatesService, FixerApiInterpreter}
import ru.johnspade.s10ns.help.StartController
import ru.johnspade.s10ns.money.{DoobieExchangeRatesRefreshTimestampRepository, DoobieExchangeRatesRepository, MoneyService}
import ru.johnspade.s10ns.settings.{SettingsController, SettingsService}
import ru.johnspade.s10ns.subscription.{CreateS10nDialogController, CreateS10nDialogFsmService, CreateS10nDialogService, DoobieSubscriptionRepository, EditS10nDialogController, EditS10nDialogService, S10nsListMessageService, StateMessageService, SubscriptionListController, SubscriptionListService}
import ru.johnspade.s10ns.telegram.{CbDataService, DialogEngine}
import ru.johnspade.s10ns.user.{DoobieUserRepository, EditS10nDialogFsmService}
import telegramium.bots.client.ApiHttp4sImp

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

object BotApp extends IOApp {
  private val userRepo = new DoobieUserRepository
  private val s10nRepo = new DoobieSubscriptionRepository
  private val exchangeRatesRepo = new DoobieExchangeRatesRepository
  private val exchangeRatesRefreshTimestampRepo = new DoobieExchangeRatesRefreshTimestampRepository

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
    )(implicit xa: Transactor[F]) =
      BlazeClientBuilder[F](ExecutionContext.global).resource.use { httpClient =>
        val http = org.http4s.client.middleware.Logger(logBody = true, logHeaders = true)(httpClient)
        val api = new ApiHttp4sImp(http, baseUrl = s"https://api.telegram.org/bot$token")
        val bot = new SubscriptionsBot[F](
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

    def init[F[_] : ConcurrentEffect : ContextShift : Timer](conf: Config, xa: Transactor[F]) = {
      implicit val transactor: Transactor[F] = xa
      implicit val sttpBackend: SttpBackend[F, Nothing] = AsyncHttpClientCatsBackend[F]()
      val calendarService = new CalendarService[F]
      val stateMessageService = new StateMessageService[F](calendarService)

      for {
        implicit0(logger: Logger[F]) <- Slf4jLogger.create[F]
        exchangeRates <- exchangeRatesRepo.get().transact(xa)
        exchangeRatesCache <- ExchangeRatesCache.create[F](exchangeRates)
        dialogEngine = new DialogEngine[F](userRepo)
        createS10nDialogFsmService = new CreateS10nDialogFsmService[F](s10nRepo, userRepo, stateMessageService, dialogEngine)
        fixerApi = new FixerApiInterpreter[F](conf.fixer.token)
        exchangeRatesService = new ExchangeRatesService[F](
          fixerApi,
          exchangeRatesRepo,
          exchangeRatesRefreshTimestampRepo,
          exchangeRatesCache
        )
        cbDataService = new CbDataService[F]
        moneyService = new MoneyService[F](exchangeRatesService)
        s10nsListService = new S10nsListMessageService[F](moneyService)
        editS10nDialogFsmService = new EditS10nDialogFsmService[F](
          s10nsListService,
          stateMessageService,
          userRepo,
          s10nRepo,
          dialogEngine
        )
        editS10nDialogService = new EditS10nDialogService[F](
          userRepo,
          s10nRepo,
          editS10nDialogFsmService,
          stateMessageService,
          dialogEngine
        )
        s10nCbService = new SubscriptionListService[F](userRepo, s10nRepo, s10nsListService)
        createS10nDialogService = new CreateS10nDialogService[F](
          userRepo,
          createS10nDialogFsmService,
          stateMessageService,
          dialogEngine
        )
        settingsService = new SettingsService[F](dialogEngine)
        exchangeRatesJobService = new ExchangeRatesJobService[F](exchangeRatesService, exchangeRatesRefreshTimestampRepo)
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
          xa.configure { dataSource =>
            IO {
              val flyWay = Flyway.configure().dataSource(dataSource).load()
              flyWay.migrate()
              ()
            }
          } *>
            init[IO](conf, xa).map(_ => ExitCode.Success)
        }
    }
  }
}
