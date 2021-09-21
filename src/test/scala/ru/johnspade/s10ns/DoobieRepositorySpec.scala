package ru.johnspade.s10ns

import cats.effect.IO
import doobie.Transactor
import doobie.scalatest.IOChecker
import org.scalatest.funsuite.AnyFunSuite
import ru.johnspade.s10ns.PostgresContainer.container

abstract class DoobieRepositorySpec extends AnyFunSuite with IOChecker {
  import container.{container => pgContainer}

  override lazy val transactor: Transactor[IO] = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    pgContainer.getJdbcUrl,
    pgContainer.getUsername,
    pgContainer.getPassword
  )
}
