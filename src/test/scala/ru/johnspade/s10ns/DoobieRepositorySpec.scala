package ru.johnspade.s10ns

import cats.effect.{ContextShift, IO}
import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}
import doobie.Transactor
import doobie.scalatest.IOChecker
import org.flywaydb.core.Flyway
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.ExecutionContext

abstract class DoobieRepositorySpec extends AnyFunSuite with BeforeAndAfterAll with ForAllTestContainer with IOChecker {
  override val container: PostgreSQLContainer = PostgreSQLContainer()

  import container.{container => pgContainer}

  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  override def transactor: Transactor[IO] = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    pgContainer.getJdbcUrl,
    pgContainer.getUsername,
    pgContainer.getPassword
  )
  
  override protected def beforeAll(): Unit = {
    Flyway
      .configure()
      .dataSource(pgContainer.getJdbcUrl, pgContainer.getUsername, pgContainer.getPassword)
      .load()
      .migrate
  }
}
