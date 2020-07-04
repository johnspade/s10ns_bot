package ru.johnspade.s10ns

import org.scalamock.scalatest.MockFactory
import telegramium.bots.client.{Method, MethodReq}
import telegramium.bots.high.Api

object TelegramiumScalamockUtils extends MockFactory {
  def verifyMethodCall[F[_], Res](api: Api[F], method: Method[Res]) =
    (api.execute[Res] _)
      .verify(where { m: Method[Res] =>
        val actual = m.asInstanceOf[MethodReq[Res]]
        val expected = method.asInstanceOf[MethodReq[Res]]
        actual.name == expected.name && actual.json == expected.json && actual.files == expected.files
      })
}
