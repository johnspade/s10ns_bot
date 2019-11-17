package ru.johnspade.s10ns.exchangerates

import cats.effect.Sync
import cats.implicits._
import com.softwaremill.sttp._
import com.softwaremill.sttp.circe._
import io.chrisdavenport.log4cats.Logger
import io.circe.generic.auto._
import io.circe.Error

class FixerApiInterpreter[F[_] : Sync : Logger](private val token: String)(implicit sttpBackend: SttpBackend[F, Nothing])
  extends FixerApi[F] {
  override def getLatestRates: F[ExchangeRates] = {
    def handleErrors(response: Response[Either[DeserializationError[Error], ExchangeRates]]) =
      response.body match {
        case Left(e) =>
          cats.MonadError[F, Throwable].raiseError[Either[DeserializationError[Error], ExchangeRates]](new RuntimeException(e))
        case Right(body) => Sync[F].pure(body)
      }

    def handleDeserializationErrors(body: Either[DeserializationError[Error], ExchangeRates]) =
      body match {
        case Left(e) => cats.MonadError[F, Throwable].raiseError[ExchangeRates](new RuntimeException(e.message))
        case Right(rates) => Sync[F].pure(rates)
      }

    sttp.get(uri"http://data.fixer.io/api/latest?access_key=$token")
      .response(asJson[ExchangeRates])
      .send()
      .flatMap(handleErrors(_).flatMap(handleDeserializationErrors))
  }
}
