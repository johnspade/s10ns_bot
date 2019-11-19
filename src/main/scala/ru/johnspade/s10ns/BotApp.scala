package ru.johnspade.s10ns

import java.time.{Instant, LocalDate, LocalDateTime, LocalTime, ZoneId}
import java.util.concurrent.TimeUnit

import cats.effect.{Async, Blocker, Clock, Concurrent, ConcurrentEffect, ContextShift, ExitCode, IO, IOApp, Sync, Timer}
import cats.implicits._
import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.asynchttpclient.cats.AsyncHttpClientCatsBackend
import doobie.hikari.HikariTransactor
import doobie.implicits._
import doobie.util.ExecutionContexts
import doobie.util.transactor.Transactor
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.client.blaze.BlazeClientBuilder
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import pureconfig.module.catseffect._
import ru.johnspade.s10ns.calendar.{CalendarController, CalendarService}
import ru.johnspade.s10ns.common.Config
import ru.johnspade.s10ns.exchangerates.{ExchangeRatesCache, ExchangeRatesService, FixerApiInterpreter}
import ru.johnspade.s10ns.help.{StartController, StartService}
import ru.johnspade.s10ns.money.{DoobieExchangeRatesRefreshTimestampRepository, DoobieExchangeRatesRepository, MoneyService}
import ru.johnspade.s10ns.settings.{SettingsController, SettingsService}
import ru.johnspade.s10ns.subscription.{CreateS10nDialogController, CreateS10nDialogFsmService, CreateS10nDialogService, DoobieSubscriptionRepository, EditS10nDialogController, EditS10nDialogService, S10nsListMessageService, SubscriptionListController, SubscriptionListService}
import ru.johnspade.s10ns.telegram.StateMessageService
import ru.johnspade.s10ns.user.{DoobieUserRepository, EditS10nDialogFsmService}
import telegramium.bots.client.ApiHttp4sImp

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

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

    def repeatDaily[F[_] : Sync : Timer](io: F[Unit]): F[Unit] = io >> Timer[F].sleep(24.hours) >> repeatDaily(io)

    def startExchangeRatesJob[F[_] : Concurrent : Timer : Logger](
      exchangeRatesService: ExchangeRatesService[F],
      xa: Transactor[F]
    ) =
      for {
        now <- Clock[F].realTime(TimeUnit.MILLISECONDS)
        xRatesRefreshTimestamp <- exchangeRatesRefreshTimestampRepo.get().transact(xa)
        duration = java.time.Duration.between(xRatesRefreshTimestamp, Instant.ofEpochMilli(now)).toHours
        initRates = if (duration > 24)
          exchangeRatesService.saveRates()
        else
          Sync[F].unit
        midnight <- Sync[F].delay {
          LocalDateTime.of(LocalDate.now, LocalTime.MIDNIGHT).plusDays(1).atZone(ZoneId.systemDefault()).toInstant.toEpochMilli
        }
        _ <- Concurrent[F].start(initRates >> Timer[F].sleep((midnight - now).millis) >> repeatDaily(exchangeRatesService.saveRates()))
      } yield ()

    def startBot[F[_] : Sync : ConcurrentEffect : ContextShift : Logger : Timer](
      token: String,
      s10nListController: SubscriptionListController[F],
      createS10nDialogController: CreateS10nDialogController[F],
      editS10nDialogController: EditS10nDialogController[F],
      settingsController: SettingsController[F],
      calendarController: CalendarController[F],
      startController: StartController[F],
      xa: Transactor[F]
    ) =
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
          xa
        )
        bot.start().handleErrorWith(e => Logger[F].error(e)(e.getMessage))
      }

    def init[F[_] : ConcurrentEffect : ContextShift : Timer](conf: Config, xa: Transactor[F]) = {
      implicit val sttpBackend: SttpBackend[F, Nothing] = AsyncHttpClientCatsBackend[F]()
      val calendarService = new CalendarService[F]
      val stateMessageService = new StateMessageService[F](calendarService)

      for {
        implicit0(logger: Logger[F]) <- Slf4jLogger.create[F]
        exchangeRates <- exchangeRatesRepo.get().transact(xa)
        exchangeRatesCache <- ExchangeRatesCache.create[F](exchangeRates)
        createS10nDialogFsmService = new CreateS10nDialogFsmService[F](s10nRepo, userRepo, xa, stateMessageService)
        fixerApi = new FixerApiInterpreter[F](conf.fixer.token)
        exchangeRatesService = new ExchangeRatesService[F](
          fixerApi,
          exchangeRatesRepo,
          exchangeRatesRefreshTimestampRepo,
          exchangeRatesCache,
          xa
        )
        moneyService = new MoneyService(exchangeRatesService)
        s10nsListService = new S10nsListMessageService[F](userRepo, s10nRepo, moneyService, xa)
        editS10nDialogFsmService = new EditS10nDialogFsmService[F](s10nsListService, stateMessageService, userRepo, s10nRepo, xa)
        editS10nDialogService = new EditS10nDialogService[F](editS10nDialogFsmService)
        s10nCbService = new SubscriptionListService[F](userRepo, s10nRepo, xa, s10nsListService)
        createS10nDialogService = new CreateS10nDialogService[F](userRepo, createS10nDialogFsmService, stateMessageService, xa)
        settingsService = new SettingsService[F](userRepo, xa)
        startService = new StartService[F](userRepo, xa)
        s10nListController = new SubscriptionListController[F](s10nCbService)
        createS10nDialogController = new CreateS10nDialogController[F](createS10nDialogService)
        editS10nDialogController = new EditS10nDialogController[F](editS10nDialogService)
        settingsController = new SettingsController[F](settingsService)
        calendarController = new CalendarController[F](calendarService)
        startController = new StartController[F](startService)
        _ <- startExchangeRatesJob[F](exchangeRatesService, xa)
        _ <- startBot[F](
          conf.telegram.token,
          s10nListController,
          createS10nDialogController,
          editS10nDialogController,
          settingsController,
          calendarController,
          startController,
          xa
        )
      } yield ()
    }

    ConfigSource.file("config.properties").loadF[IO, Config].flatMap { conf =>
      createTransactor[IO](conf.app.db).use(init[IO](conf, _).map(_ => ExitCode.Success))
    }
  }
}
