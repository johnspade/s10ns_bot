package ru.johnspade.s10ns.exchangerates

import cats.Monad
import cats.MonadError
import cats.implicits._

import io.circe.Error
import io.circe.generic.auto._
import sttp.client3._
import sttp.client3.circe._

class FixerApiInterpreter[F[_]](private val token: String, sttpBackend: SttpBackend[F, Any])(implicit
    monadError: MonadError[F, Throwable]
) extends FixerApi[F] {
  override def getLatestRates: F[ExchangeRates] = {
    def handleErrors(response: Response[Either[ResponseException[String, Error], ExchangeRates]]) =
      response.body match {
        case Left(e) =>
          monadError.raiseError[ExchangeRates](new RuntimeException(e))
        case Right(body) => Monad[F].pure(body)
      }

    basicRequest
      .get(uri"http://data.fixer.io/api/latest?access_key=$token")
      .response(asJson[ExchangeRates])
      .send(sttpBackend)
      .flatMap(handleErrors)
  }
}
