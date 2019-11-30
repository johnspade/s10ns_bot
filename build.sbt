name := "s10ns_bot"

version := "0.1"

scalaVersion := "2.12.9"

scalacOptions ++= Seq(
  "-Ypartial-unification",
  "-language:higherKinds"
)

val DoobieVersion = "0.8.0-M1"
val FuuidVersion = "0.2.0"
val CirceVersion = "0.11.1"
val SttpVersion = "1.6.4"
val PureconfigVersion = "0.12.1"
val CatsRetryVersion = "0.3.1"
val KantanVersion = "0.6.0"

lazy val telegramiumHigh = ProjectRef(uri("https://github.com/apimorphism/telegramium.git#master"), "telegramium-high")
lazy val root = project.in(file(".")).dependsOn(telegramiumHigh)

libraryDependencies ++= Seq(
  "com.softwaremill.sttp" %% "core" % SttpVersion,
  "com.softwaremill.sttp" %% "async-http-client-backend-cats" % SttpVersion,
  "com.softwaremill.sttp" %% "circe" % SttpVersion,
  "org.typelevel" %% "cats-effect" % "2.0.0-M4",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "io.circe" %% "circe-core" % CirceVersion,
  "io.circe" %% "circe-generic" % CirceVersion,
  "io.circe" %% "circe-generic-extras" % CirceVersion,
  "io.circe" %% "circe-parser" % CirceVersion,
  "org.tpolecat" %% "doobie-core" % DoobieVersion,
  "org.tpolecat" %% "doobie-postgres" % DoobieVersion,
  "org.tpolecat" %% "doobie-hikari" % DoobieVersion,
  "com.beachape" %% "enumeratum" % "1.5.13",
  "com.beachape" %% "enumeratum-circe" % "1.5.22",
  "com.beachape" %% "enumeratum-doobie" % "1.5.15",
  "org.joda" % "joda-money" % "1.0.1",
  "io.chrisdavenport" %% "log4cats-slf4j" % "1.0.1",
  "com.github.pureconfig" %% "pureconfig" % PureconfigVersion,
  "com.github.pureconfig" %% "pureconfig-cats-effect" % PureconfigVersion,
  "com.github.cb372" %% "cats-retry-core" % CatsRetryVersion,
  "com.github.cb372" %% "cats-retry-cats-effect" % CatsRetryVersion,
  "com.nrinaudo" %% "kantan.csv" % KantanVersion,
  "com.nrinaudo" %% "kantan.csv-java8" % KantanVersion,
  "org.rudogma" %% "supertagged" % "1.4"
)

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
