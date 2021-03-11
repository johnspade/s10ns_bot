import TestSettings._

name := "s10ns_bot"

version := "0.1"

scalaVersion := "2.13.5"

scalacOptions ++= Seq(
  "-language:higherKinds"
)

val TelegramiumVersion = "4.51.0"
val TgbotUtilsVersion = "0.3.0"
val DoobieVersion = "0.10.0"
val FuuidVersion = "0.5.0"
val CirceVersion = "0.13.0"
val SttpVersion = "1.7.2"
val PureconfigVersion = "0.14.1"
val CatsRetryVersion = "0.3.2"
val KantanVersion = "0.6.1"
val EnumeratumVersion = "1.6.0"
val TestcontainersScalaVersion = "0.39.3"

libraryDependencies ++= Seq(
  "io.github.apimorphism" %% "telegramium-core" % TelegramiumVersion,
  "io.github.apimorphism" %% "telegramium-high" % TelegramiumVersion,
  "ru.johnspade" %% "tgbot-utils" % TgbotUtilsVersion,
  "com.softwaremill.sttp" %% "core" % SttpVersion,
  "com.softwaremill.sttp" %% "async-http-client-backend-cats" % SttpVersion,
  "com.softwaremill.sttp" %% "circe" % SttpVersion,
  "org.typelevel" %% "cats-effect" % "2.3.3",
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
  "ru.tinkoff" %% "tofu-logging" % "0.9.2",
  "com.github.pureconfig" %% "pureconfig" % PureconfigVersion,
  "com.github.pureconfig" %% "pureconfig-magnolia" % PureconfigVersion,
  "com.github.cb372" %% "cats-retry-core" % CatsRetryVersion,
  "com.github.cb372" %% "cats-retry-cats-effect" % CatsRetryVersion,
  "com.nrinaudo" %% "kantan.csv" % KantanVersion,
  "com.nrinaudo" %% "kantan.csv-java8" % KantanVersion,
  "com.nrinaudo" %% "kantan.csv-enumeratum" % KantanVersion,
  "org.rudogma" %% "supertagged" % "1.5",
  "org.flywaydb" % "flyway-core" % "7.6.0",
  "com.softwaremill.quicklens" %% "quicklens" % "1.6.1",
  "com.ibm.icu" % "icu4j" % "68.2",
  "io.chrisdavenport" %% "fuuid" % FuuidVersion,
  "io.chrisdavenport" %% "fuuid-doobie" % FuuidVersion,
  "org.scalatest" %% "scalatest" % "3.2.6" % Test,
  "com.dimafeng" %% "testcontainers-scala-scalatest" % TestcontainersScalaVersion % Test,
  "com.dimafeng" %% "testcontainers-scala-postgresql" % TestcontainersScalaVersion % Test,
  "com.softwaremill.diffx" %% "diffx-scalatest" % "0.4.4" % Test,
  "org.scalamock" %% "scalamock" % "5.1.0" % Test
)

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.3" cross CrossVersion.full)

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", _) => MergeStrategy.discard
  case _ => MergeStrategy.first
}

resolvers += Resolver.bintrayRepo("johnspade", "maven")

//lazy val `tgbot-utils` = ProjectRef(uri("https://github.com/johnspade/tgbot-utils.git#master"), "root")
lazy val root: Project = (project in file("."))
  .withSerialTests
//  .dependsOn(`tgbot-utils`)
