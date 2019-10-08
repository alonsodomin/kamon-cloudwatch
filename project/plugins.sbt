lazy val root: Project = project.in(file(".")).dependsOn(latestSbtUmbrella)
lazy val latestSbtUmbrella = RootProject(
  uri("git://github.com/kamon-io/kamon-sbt-umbrella.git#kamon-2.x")
)

addSbtPlugin("com.jsuereth"   % "sbt-pgp"      % "1.1.2")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.6")
addSbtPlugin("com.dwijnand"   % "sbt-travisci" % "1.2.0")
addSbtPlugin("org.scalameta"  % "sbt-scalafmt" % "2.0.5")
