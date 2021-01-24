package ru.johnspade.s10ns

import org.scalamock.handlers.{CallHandler1, Verify}
import org.scalamock.scalatest.MockFactory
import telegramium.bots.client.Method
import telegramium.bots.high.Api

object TelegramiumScalamockUtils extends MockFactory {
  def verifyMethodCall[F[_], Res](api: Api[F], method: Method[Res]): CallHandler1[Method[Res], F[Res]] with Verify =
    (api.execute[Res] _)
      .verify(where {
        (_: Method[Res]).payload == method.payload
      })
}
