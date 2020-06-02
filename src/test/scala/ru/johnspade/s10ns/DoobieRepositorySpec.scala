package ru.johnspade.s10ns

import cats.effect.{ContextShift, IO}
import doobie.Transactor
import doobie.scalatest.IOChecker
import org.scalatest.funsuite.AnyFunSuite
import ru.johnspade.s10ns.PostgresContainer.container

import scala.concurrent.ExecutionContext

abstract class DoobieRepositorySpec extends AnyFunSuite with IOChecker {
  import container.{container => pgContainer}

  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  override lazy val transactor: Transactor[IO] = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    pgContainer.getJdbcUrl,
    pgContainer.getUsername,
    pgContainer.getPassword
  )
}
