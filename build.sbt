name := "s10ns_bot"

version := "0.1"

scalaVersion := "2.13.2"

scalacOptions ++= Seq(
  "-language:higherKinds"
)

val DoobieVersion = "0.9.0"
val FuuidVersion = "0.2.0"
val CirceVersion = "0.13.0"
val SttpVersion = "1.7.2"
val PureconfigVersion = "0.12.3"
val CatsRetryVersion = "0.3.2"
val KantanVersion = "0.6.0"
val EnumeratumVersion = "1.6.0"
val TestcontainersScalaVersion = "0.37.0"

libraryDependencies ++= Seq(
  "com.softwaremill.sttp" %% "core" % SttpVersion,
  "com.softwaremill.sttp" %% "async-http-client-backend-cats" % SttpVersion,
  "com.softwaremill.sttp" %% "circe" % SttpVersion,
  "org.typelevel" %% "cats-effect" % "2.1.3",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "io.circe" %% "circe-core" % CirceVersion,
  "io.circe" %% "circe-generic" % CirceVersion,
  "io.circe" %% "circe-generic-extras" % CirceVersion,
  "io.circe" %% "circe-parser" % CirceVersion,
  "org.tpolecat" %% "doobie-core" % DoobieVersion,
  "org.tpolecat" %% "doobie-postgres" % DoobieVersion,
  "org.tpolecat" %% "doobie-hikari" % DoobieVersion,
  "org.tpolecat" %% "doobie-scalatest" % DoobieVersion,
  "com.beachape" %% "enumeratum" % EnumeratumVersion,
  "com.beachape" %% "enumeratum-circe" % EnumeratumVersion,
  "com.beachape" %% "enumeratum-doobie" % EnumeratumVersion,
  "org.joda" % "joda-money" % "1.0.1",
  "io.chrisdavenport" %% "log4cats-slf4j" % "1.0.1",
  "com.github.pureconfig" %% "pureconfig" % PureconfigVersion,
  "com.github.pureconfig" %% "pureconfig-cats-effect" % PureconfigVersion,
  "com.github.cb372" %% "cats-retry-core" % CatsRetryVersion,
  "com.github.cb372" %% "cats-retry-cats-effect" % CatsRetryVersion,
  "com.nrinaudo" %% "kantan.csv" % KantanVersion,
  "com.nrinaudo" %% "kantan.csv-java8" % KantanVersion,
  "com.nrinaudo" %% "kantan.csv-enumeratum" % KantanVersion,
  "org.rudogma" %% "supertagged" % "1.5",
  "org.flywaydb" % "flyway-core" % "6.4.1",
  "com.propensive" %% "magnolia" % "0.16.0",
  "com.softwaremill.quicklens" %% "quicklens" % "1.5.0",
  "com.ibm.icu" % "icu4j" % "65.1",
  "org.scalatest" %% "scalatest" % "3.1.1" % Test,
  "com.dimafeng" %% "testcontainers-scala-scalatest" % TestcontainersScalaVersion % Test,
  "com.dimafeng" %% "testcontainers-scala-postgresql" % TestcontainersScalaVersion % Test,
  "com.softwaremill.diffx" %% "diffx-scalatest" % "0.3.28" % Test,
  "org.scalamock" %% "scalamock" % "4.4.0" % Test
)

lazy val telegramiumCore = ProjectRef(uri("https://github.com/apimorphism/telegramium.git#master"), "telegramium-core")
lazy val telegramiumHigh = ProjectRef(uri("https://github.com/apimorphism/telegramium.git#master"), "telegramium-high")
lazy val root = project.in(file(".")).dependsOn(telegramiumCore, telegramiumHigh)

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", _) => MergeStrategy.discard
  case _ => MergeStrategy.first
}
