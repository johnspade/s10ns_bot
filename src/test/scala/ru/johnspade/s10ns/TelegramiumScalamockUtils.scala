package ru.johnspade.s10ns

import org.scalamock.handlers.CallHandler1
import org.scalamock.handlers.Verify
import org.scalamock.scalatest.MockFactory
import telegramium.bots.client.Method
import telegramium.bots.high.Api

trait TelegramiumScalamockUtils {
  this: MockFactory =>
  def verifyMethodCall[F[_], Res](api: Api[F], method: Method[Res]): CallHandler1[Method[Res], F[Res]] & Verify =
    (api
      .execute[Res])
      .verify(where {
        (_: Method[Res]).payload == method.payload
      })
}
