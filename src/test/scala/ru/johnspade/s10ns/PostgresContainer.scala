package ru.johnspade.s10ns

import cats.effect.IO
import cats.~>
import com.dimafeng.testcontainers.PostgreSQLContainer
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.flywaydb.core.Flyway
import org.testcontainers.utility.DockerImageName

import scala.concurrent.ExecutionContext

object PostgresContainer {
  lazy val container: PostgreSQLContainer = {
    val instance = PostgreSQLContainer(dockerImageNameOverride = DockerImageName.parse("postgres:12.6"))
    instance.container.start()

    import instance.{container => pgContainer}

    Flyway
      .configure()
      .dataSource(pgContainer.getJdbcUrl, pgContainer.getUsername, pgContainer.getPassword)
      .load()
      .migrate

    instance
  }

  implicit lazy val xa: Transactor[IO] = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    container.jdbcUrl,
    container.username,
    container.password
  )
  implicit val transact: ~>[ConnectionIO, IO] = new ~>[ConnectionIO, IO] {
    override def apply[A](fa: ConnectionIO[A]): IO[A] = fa.transact(xa)
  }
}
