package ru.johnspade.s10ns.bot.engine

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ru.johnspade.tgbot.callbackqueries.CallbackDataDecoder
import ru.johnspade.tgbot.callbackqueries.CallbackQueryDsl._
import ru.johnspade.tgbot.callbackqueries.CallbackQueryHandler
import ru.johnspade.tgbot.callbackqueries.CallbackQueryRoutes
import ru.johnspade.tgbot.callbackqueries.DecodeResult
import telegramium.bots.CallbackQuery
import telegramium.bots.ChatIntId
import telegramium.bots.User
import telegramium.bots.client.Method
import telegramium.bots.high.Api
import telegramium.bots.high.Methods

import ru.johnspade.s10ns.TelegramiumScalamockUtils.verifyMethodCall

class CallbackQueryHandlerSpec extends AnyFlatSpec with Matchers with MockFactory {
  private implicit val api: Api[IO] = stub[Api[IO]]
  private val routes = CallbackQueryRoutes.of[String, Unit, IO] {
    case "test1" in cb => api.execute(Methods.answerCallbackQuery(cb.id)).void
    case "test2" in _ => api.execute(Methods.getMe()).void
  }
  private val testCallbackDataDecoder: CallbackDataDecoder[IO, String] = DecodeResult.success[IO, String](_)

  private val testUser = User(0, isBot = false, "")

  "handle" should "use correct routes" in {
    (api.execute[Boolean] _)
      .when(where((_: Method[Boolean]).payload.name == "answerCallbackQuery"))
      .returns(IO.pure(true))
    (api.execute[User] _)
      .when(where((_: Method[User]).payload.name == "getMe"))
      .returns(IO.pure(testUser))
    (api.execute[Boolean] _)
      .when(where((_: Method[Boolean]).payload.name == "leaveChat"))
      .returns(IO.pure(true))

    CallbackQueryHandler.handle[IO, String, Unit](createCb("1", "test1"), routes, testCallbackDataDecoder, ifNotFound).unsafeRunSync()
    verifyMethodCall(api, Methods.answerCallbackQuery("1"))

    CallbackQueryHandler.handle[IO, String, Unit](createCb("2", "test2"), routes, testCallbackDataDecoder, ifNotFound).unsafeRunSync()
    verifyMethodCall(api, Methods.getMe())

    CallbackQueryHandler.handle[IO, String, Unit](createCb("3", "test3"), routes, testCallbackDataDecoder, ifNotFound).unsafeRunSync()
    verifyMethodCall(api, Methods.leaveChat(ChatIntId(911)))
  }

  private def createCb(id: String, data: String) = CallbackQuery(id, testUser, chatInstance = "", data = Some(data))

  private def ifNotFound = (_: CallbackQuery) => api.execute(Methods.leaveChat(ChatIntId(911))).void
}
