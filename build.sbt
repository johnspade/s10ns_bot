name := "s10ns_bot"

version := "0.1"

scalaVersion := "2.12.9"

scalacOptions ++= Seq(
  "-Ypartial-unification",
  "-language:higherKinds"
)

val DoobieVersion = "0.8.0-M1"
val FuuidVersion = "0.2.0"
val CirceVersion = "0.12.3"
val SttpVersion = "1.6.4"
val PureconfigVersion = "0.12.1"
val CatsRetryVersion = "0.3.1"
val KantanVersion = "0.6.0"

libraryDependencies ++= Seq(
  "io.github.apimorphism" %% "telegramium-core" % "1.0.0-RC1",
  "io.github.apimorphism" %% "telegramium-high" % "1.0.0-RC1",
  "com.softwaremill.sttp" %% "core" % SttpVersion,
  "com.softwaremill.sttp" %% "async-http-client-backend-cats" % SttpVersion,
  "com.softwaremill.sttp" %% "circe" % SttpVersion,
  "org.typelevel" %% "cats-effect" % "2.0.0-M4",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "io.circe" %% "circe-core" % CirceVersion,
  "io.circe" %% "circe-generic" % CirceVersion,
  "io.circe" %% "circe-generic-extras" % "0.12.2",
  "io.circe" %% "circe-parser" % CirceVersion,
  "org.tpolecat" %% "doobie-core" % DoobieVersion,
  "org.tpolecat" %% "doobie-postgres" % DoobieVersion,
  "org.tpolecat" %% "doobie-hikari" % DoobieVersion,
  "org.tpolecat" %% "doobie-scalatest" % DoobieVersion,
  "com.beachape" %% "enumeratum" % "1.5.13",
  "com.beachape" %% "enumeratum-circe" % "1.5.20",
  "com.beachape" %% "enumeratum-doobie" % "1.5.15",
  "org.joda" % "joda-money" % "1.0.1",
  "io.chrisdavenport" %% "log4cats-slf4j" % "1.0.1",
  "com.github.pureconfig" %% "pureconfig" % PureconfigVersion,
  "com.github.pureconfig" %% "pureconfig-cats-effect" % PureconfigVersion,
  "com.github.cb372" %% "cats-retry-core" % CatsRetryVersion,
  "com.github.cb372" %% "cats-retry-cats-effect" % CatsRetryVersion,
  "com.nrinaudo" %% "kantan.csv" % KantanVersion,
  "com.nrinaudo" %% "kantan.csv-java8" % KantanVersion,
  "com.nrinaudo" %% "kantan.csv-enumeratum" % KantanVersion,
  "org.rudogma" %% "supertagged" % "1.4",
  "org.flywaydb" % "flyway-core" % "6.1.1",
  "com.propensive" %% "magnolia" % "0.12.3",
  "com.softwaremill.quicklens" %% "quicklens" % "1.4.12",
  "com.ibm.icu" % "icu4j" % "65.1",
  "org.scalatest" %% "scalatest" % "3.1.0" % "test",
  "com.dimafeng" %% "testcontainers-scala-postgresql" % "0.35.0" % "test",
  "com.softwaremill.diffx" %% "diffx-scalatest" % "0.3.16" % "test"
)

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
