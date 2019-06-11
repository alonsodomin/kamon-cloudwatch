lazy val root: Project = project.in(file(".")).dependsOn(latestSbtUmbrella)
lazy val latestSbtUmbrella = RootProject(uri("git://github.com/kamon-io/kamon-sbt-umbrella.git#kamon-1.x"))

addSbtPlugin("com.jsuereth"   % "sbt-pgp"      % "1.1.2")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.3")