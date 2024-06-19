package ru.johnspade.s10ns.exchangerates

import cats.Monad
import cats.MonadError
import cats.implicits.*

import io.circe.Error
import io.circe.generic.auto.*
import sttp.client3.*
import sttp.client3.circe.*

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
