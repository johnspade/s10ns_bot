import sbt.Keys.{baseDirectory, fork, parallelExecution, testOptions}
import sbt.{Def, Defaults, Keys, Tests, config, file, inConfig, _}

object TestSettings {
  private lazy val testSettings: Seq[Def.Setting[_]] = inConfig(Test)(
    Seq(
      fork := false,
      testOptions := Seq(Tests.Filter(!isIntegrationSpec(_)))
    )
  )

  private def isIntegrationSpec(name: String): Boolean = name endsWith "ISpec"

  private lazy val Serial = config("serial") extend Test

  private lazy val serialTestSettings: Seq[Def.Setting[_]] = inConfig(Serial)(
    Defaults.testTasks ++ Seq(
      fork := true,
      testOptions := Seq(Tests.Filter(isIntegrationSpec)),
      parallelExecution := false,
      baseDirectory := file(s"${Keys.name.value}/target")
    )
  )

  implicit class CustomFlowOps(val project: Project) extends AnyVal {
    def withSerialTests: Project = project
      .settings(testSettings: _*)
      .settings(serialTestSettings: _*)
      .configs(Serial)
  }
}
