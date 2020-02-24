package ru.johnspade.s10ns

import cats.effect.{Async, Blocker, ConcurrentEffect, ContextShift, ExitCode, IO, IOApp, Sync, Timer}
import cats.implicits._
import cats.{Monad, ~>}
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
import ru.johnspade.s10ns.bot.engine.{DefaultDialogEngine, DefaultMsgService, TransactionalDialogEngine}
import ru.johnspade.s10ns.bot.{CbDataService, Config, MoneyService, StartController}
import ru.johnspade.s10ns.calendar.{CalendarController, CalendarService}
import ru.johnspade.s10ns.exchangerates.{DefaultExchangeRatesJobService, DefaultExchangeRatesService, DoobieExchangeRatesRefreshTimestampRepository, DoobieExchangeRatesRepository, ExchangeRatesCache, ExchangeRatesRefreshTimestampRepository, ExchangeRatesRepository, FixerApiInterpreter}
import ru.johnspade.s10ns.settings.{DefaultSettingsService, SettingsController, SettingsDialogState}
import ru.johnspade.s10ns.subscription.controller.{CreateS10nDialogController, EditS10nDialogController, SubscriptionListController}
import ru.johnspade.s10ns.subscription.dialog.{CreateS10nMsgService, EditS10n1stPaymentDateMsgService, EditS10nAmountDialogState, EditS10nBillingPeriodDialogState, EditS10nCurrencyDialogState, EditS10nNameDialogState, EditS10nOneTimeDialogState}
import ru.johnspade.s10ns.subscription.repository.{DoobieSubscriptionRepository, SubscriptionRepository}
import ru.johnspade.s10ns.subscription.service.impl.{DefaultCreateS10nDialogFsmService, DefaultCreateS10nDialogService, DefaultEditS10n1stPaymentDateDialogService, DefaultEditS10nAmountDialogService, DefaultEditS10nBillingPeriodDialogService, DefaultEditS10nCurrencyDialogService, DefaultEditS10nNameDialogService, DefaultEditS10nOneTimeDialogService, DefaultSubscriptionListService}
import ru.johnspade.s10ns.subscription.service.{S10nInfoService, S10nsListMessageService}
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

    def createEditS10nDialogController[F[_] : Sync : Logger : Timer, D[_] : Monad](
      userRepo: UserRepository[D],
      s10nRepo: SubscriptionRepository[D],
      dialogEngine: TransactionalDialogEngine[F, D],
      s10nsListService: S10nsListMessageService[F],
      calendarService: CalendarService
    )(implicit transact: D ~> F): EditS10nDialogController[F] = {
      val editS10n1stPaymentDateDialogService = new DefaultEditS10n1stPaymentDateDialogService[F, D](
        s10nsListService, new EditS10n1stPaymentDateMsgService[F](calendarService), userRepo, s10nRepo, dialogEngine
      )
      val editS10nNameDialogService = new DefaultEditS10nNameDialogService[F, D](
        s10nsListService, new DefaultMsgService[F, EditS10nNameDialogState], userRepo, s10nRepo, dialogEngine
      )
      val editS10nAmountDialogService = new DefaultEditS10nAmountDialogService[F, D](
        s10nsListService, new DefaultMsgService[F, EditS10nAmountDialogState], userRepo, s10nRepo, dialogEngine
      )
      val editS10nBillingPeriodDialogService = new DefaultEditS10nBillingPeriodDialogService[F, D](
        s10nsListService, new DefaultMsgService[F, EditS10nBillingPeriodDialogState], userRepo, s10nRepo, dialogEngine
      )
      val editS10nCurrencyDialogService = new DefaultEditS10nCurrencyDialogService[F, D](
        s10nsListService, new DefaultMsgService[F, EditS10nCurrencyDialogState], userRepo, s10nRepo, dialogEngine
      )
      val editS10nOneTimeDialogService = new DefaultEditS10nOneTimeDialogService[F, D](
        s10nsListService, new DefaultMsgService[F, EditS10nOneTimeDialogState], userRepo, s10nRepo, dialogEngine
      )
      new EditS10nDialogController[F](
        editS10n1stPaymentDateDialogService,
        editS10nNameDialogService,
        editS10nAmountDialogService,
        editS10nBillingPeriodDialogService,
        editS10nCurrencyDialogService,
        editS10nOneTimeDialogService
      )
    }

    def init[F[_] : ConcurrentEffect : ContextShift : Timer](conf: Config, transact: D ~> F) = {
      implicit val xa: D ~> F = transact
      implicit val sttpBackend: SttpBackend[F, Nothing] = AsyncHttpClientCatsBackend[F]()
      val calendarService = new CalendarService

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
        createS10nMsgService = new CreateS10nMsgService[F](calendarService)
        createS10nDialogFsmService = new DefaultCreateS10nDialogFsmService[F, D](
          s10nRepo,
          userRepo,
          dialogEngine,
          s10nsListService,
          createS10nMsgService
        )
        cbDataService = new CbDataService[F]
        s10nCbService = new DefaultSubscriptionListService[F, D](s10nRepo, s10nsListService)
        createS10nDialogService = new DefaultCreateS10nDialogService[F, D](
          userRepo,
          createS10nDialogFsmService,
          createS10nMsgService,
          dialogEngine
        )
        settingsMsgService = new DefaultMsgService[F, SettingsDialogState]
        settingsService = new DefaultSettingsService[F](dialogEngine, settingsMsgService)
        exchangeRatesJobService = new DefaultExchangeRatesJobService[F, D](
          exchangeRatesService,
          exchangeRatesRefreshTimestampRepo
        )
        s10nListController = new SubscriptionListController[F](s10nCbService)
        createS10nDialogController = new CreateS10nDialogController[F](createS10nDialogService)
        editS10nDialogController = createEditS10nDialogController[F, D](
          userRepo, s10nRepo, dialogEngine, s10nsListService, calendarService
        )
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
