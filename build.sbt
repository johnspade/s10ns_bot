import TestSettings._

name := "s10ns_bot"

inThisBuild(
  List(
    scalaVersion      := "2.13.13",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision
  )
)

scalacOptions ++= Seq(
  "-language:higherKinds",
  "-Wunused:imports",
  "-Wunused:locals",
  "-Wunused:privates",
  "-deprecation"
)

val CatsRetryVersion           = "3.1.0"
val CirceVersion               = "0.14.6"
val DoobieVersion              = "1.0.0-RC5"
val EnumeratumVersion          = "1.7.3"
val FlywayVersion              = "10.8.1"
val KantanVersion              = "0.7.0"
val PureconfigVersion          = "0.17.6"
val SttpVersion                = "3.9.3"
val TelegramiumVersion         = "7.54.0"
val TestcontainersScalaVersion = "0.41.3"
val TgbotUtilsVersion          = "0.5.0"
val TofuVersion                = "0.12.0.1"

libraryDependencies ++= Seq(
  "io.github.apimorphism"         %% "telegramium-core"                % TelegramiumVersion,
  "io.github.apimorphism"         %% "telegramium-high"                % TelegramiumVersion,
  "ru.johnspade"                  %% "tgbot-utils"                     % TgbotUtilsVersion,
  "com.softwaremill.sttp.client3" %% "core"                            % SttpVersion,
  "com.softwaremill.sttp.client3" %% "async-http-client-backend-cats"  % SttpVersion,
  "com.softwaremill.sttp.client3" %% "circe"                           % SttpVersion,
  "org.typelevel"                 %% "cats-effect"                     % "3.5.3",
  "ch.qos.logback"                 % "logback-classic"                 % "1.5.1",
  "io.circe"                      %% "circe-core"                      % CirceVersion,
  "io.circe"                      %% "circe-generic"                   % CirceVersion,
  "io.circe"                      %% "circe-generic-extras"            % "0.14.3",
  "io.circe"                      %% "circe-parser"                    % CirceVersion,
  "org.tpolecat"                  %% "doobie-core"                     % DoobieVersion,
  "org.tpolecat"                  %% "doobie-postgres"                 % DoobieVersion,
  "org.tpolecat"                  %% "doobie-hikari"                   % DoobieVersion,
  "org.tpolecat"                  %% "doobie-scalatest"                % DoobieVersion,
  "com.beachape"                  %% "enumeratum"                      % EnumeratumVersion,
  "com.beachape"                  %% "enumeratum-circe"                % EnumeratumVersion,
  "com.beachape"                  %% "enumeratum-doobie"               % "1.7.5",
  "org.joda"                       % "joda-money"                      % "1.0.1",
  "tf.tofu"                       %% "tofu-core-ce3"                   % TofuVersion,
  "tf.tofu"                       %% "tofu-logging-derivation"         % TofuVersion,
  "tf.tofu"                       %% "tofu-logging-layout"             % TofuVersion,
  "tf.tofu"                       %% "tofu-logging-structured"         % TofuVersion,
  "com.github.pureconfig"         %% "pureconfig"                      % PureconfigVersion,
  "com.github.pureconfig"         %% "pureconfig-magnolia"             % PureconfigVersion,
  "com.github.cb372"              %% "cats-retry"                      % CatsRetryVersion,
  "com.nrinaudo"                  %% "kantan.csv"                      % KantanVersion,
  "com.nrinaudo"                  %% "kantan.csv-java8"                % KantanVersion,
  "com.nrinaudo"                  %% "kantan.csv-enumeratum"           % KantanVersion,
  "org.flywaydb"                   % "flyway-core"                     % FlywayVersion,
  "org.flywaydb"                   % "flyway-database-postgresql"      % FlywayVersion              % Runtime,
  "com.softwaremill.quicklens"    %% "quicklens"                       % "1.9.7",
  "com.ibm.icu"                    % "icu4j"                           % "74.2",
  "org.scalatest"                 %% "scalatest"                       % "3.2.18"                   % Test,
  "com.dimafeng"                  %% "testcontainers-scala-scalatest"  % TestcontainersScalaVersion % Test,
  "com.dimafeng"                  %% "testcontainers-scala-postgresql" % TestcontainersScalaVersion % Test,
  "com.softwaremill.diffx"        %% "diffx-scalatest-should"          % "0.9.0"                    % Test,
  "org.scalamock"                 %% "scalamock"                       % "6.0.0-M2"                 % Test
)

addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1")
addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.13.3" cross CrossVersion.full)

ThisBuild / dynverSeparator := "-"

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val root: Project = (project in file("."))
  .enablePlugins(JavaAppPackaging, DockerPlugin, AshScriptPlugin)
  .withSerialTests
  .settings(
    dockerBaseImage := "adoptopenjdk/openjdk11:jre-11.0.10_9-alpine",
    dockerExposedPorts ++= Seq(8080)
  )

addCommandAlias(
  "validate",
  ";compile;Test/compile;scalafixAll --check;scalafmtSbtCheck;scalafmtCheck;Test/scalafmtCheck;test;Serial/test"
)
