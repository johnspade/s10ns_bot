package ru.johnspade.s10ns.bot

import cats.effect.Sync
import kantan.csv.ops._
import ru.johnspade.s10ns.csv.MagnoliaRowDecoder._

class CbDataService[F[_] : Sync] {
  def decode(csv: String): F[CbData] =
    csv.readCsv[List, CbData](CbData.csvConfig)
      .headOption
      .map {
        case Left(e) =>
          Sync[F].raiseError[CbData](e)
        case Right(value) =>
          Sync[F].pure(value)
      }
      .getOrElse(Sync[F].raiseError(new RuntimeException("Callback data is missing")))
}
