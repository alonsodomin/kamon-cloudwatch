lazy val root: Project = project.in(file(".")).dependsOn(latestSbtUmbrella)
lazy val latestSbtUmbrella = RootProject(
  uri("git://github.com/kamon-io/kamon-sbt-umbrella.git#kamon-2.x")
)

addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.2.6")
addSbtPlugin("com.dwijnand"   % "sbt-travisci" % "1.2.0")
addSbtPlugin("org.scalameta"  % "sbt-scalafmt" % "2.0.5")
