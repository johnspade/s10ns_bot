package ru.johnspade.s10ns.subscription.service

import cats.Id
import cats.effect.{Clock, IO}
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ru.johnspade.s10ns.TestTransactor.transact
import ru.johnspade.s10ns.bot.engine.DefaultDialogEngine
import ru.johnspade.s10ns.bot.{MoneyService, StateMessageService}
import ru.johnspade.s10ns.calendar.CalendarService
import ru.johnspade.s10ns.exchangerates.InMemoryExchangeRatesStorage
import ru.johnspade.s10ns.subscription.repository.InMemorySubscriptionRepository
import ru.johnspade.s10ns.user.InMemoryUserRepository

import scala.concurrent.ExecutionContext

class DefaultCreateS10nDialogFsmServiceSpec extends AnyFlatSpec with Matchers {
  private implicit val clock: Clock[IO] = IO.timer(ExecutionContext.global).clock
  private implicit val logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.create[IO].unsafeRunSync

  private val subscriptionRepository = new InMemorySubscriptionRepository
  private val userRepository = new InMemoryUserRepository
  private val stateMessageService = new StateMessageService[IO](new CalendarService)
  private val dialogEngine = new DefaultDialogEngine[IO, Id](userRepository)
  private val moneyService = new MoneyService[IO](new InMemoryExchangeRatesStorage)
  private val s10nsListMessageService = new S10nsListMessageService[IO](moneyService, new S10nInfoService[IO](moneyService))
  private val createS10nDialogFsmService = new DefaultCreateS10nDialogFsmService[IO, Id](
    subscriptionRepository, userRepository, stateMessageService, dialogEngine, s10nsListMessageService
  )
}
