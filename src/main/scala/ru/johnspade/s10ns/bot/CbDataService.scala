package ru.johnspade.s10ns.bot

import cats.effect.Sync
import kantan.csv.DecodeError.TypeError
import kantan.csv.ReadResult
import kantan.csv.ops._
import ru.johnspade.s10ns.csv.MagnoliaRowDecoder._

class CbDataService[F[_]: Sync] {
  def decode(csv: String): ReadResult[CbData] =
    csv.readCsv[List, CbData](CbData.csvConfig)
      .headOption
      .getOrElse(Left(TypeError("Callback data is missing")))
}
