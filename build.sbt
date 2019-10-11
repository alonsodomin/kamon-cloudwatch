/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

name := "kamon-cloudwatch"
description := "Kamon extension to publish metrics into AWS CloudWatch"
startYear := Some(2018)

organization := "com.github.alonsodomin"
organizationName := "A. Alonso Dominguez"

homepage := Some(url("https://github.com/alonsodomin/kamon-cloudwatch"))

licenses += (("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")))
scalacOptions := Seq(
  "-encoding",
  "utf8",
  "-g:vars",
  "-feature",
  "-unchecked",
  "-deprecation",
  "-language:postfixOps",
  "-language:implicitConversions",
  "-Xlog-reflective-calls",
  "-Ywarn-dead-code"
)

javacOptions := Seq(
  "-Xlint:-options"
)

resolvers += Resolver.bintrayRepo("kamon-io", "releases")

scmInfo := Some(
  ScmInfo(
    url("https://github.com/alonsodomin/kamon-cloudwatch"),
    "scm:git:git@github.com:alonsodomin/kamon-cloudwatch.git"
  )
)
developers += Developer(
  "alonsodomin",
  "A. Alonso Dominguez",
  "",
  url("https://github.com/alonsodomin")
)

sonatypeProfileName := "com.github.alonsodomin"

publishTo := Some(
  if (isSnapshot.value) Opts.resolver.sonatypeSnapshots
  else Opts.resolver.sonatypeStaging
)

publishMavenStyle := true
publishArtifact in Test := false

libraryDependencies ++= Seq(
  "io.kamon"               %% "kamon-core"             % Versions.kamon,
  "io.kamon"               %% "kamon-testkit"          % Versions.kamon % Test,
  "org.slf4j"              % "slf4j-api"               % Versions.slf4j,
  "com.amazonaws"          % "aws-java-sdk-cloudwatch" % Versions.aws,
  "org.scalatest"          %% "scalatest"              % Versions.scalatest % Test,
  "com.github.tomakehurst" % "wiremock"                % Versions.wiremock % Test,
  "org.apache.logging.log4j"    % "log4j-core"                % Versions.log4j,
  "org.apache.logging.log4j"    % "log4j-api"                 % Versions.log4j,
  "org.apache.logging.log4j"    % "log4j-slf4j-impl"          % Versions.log4j
)

unmanagedSourceDirectories in Compile += {
  val sourceDir = (sourceDirectory in Compile).value
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, n)) if n <= 12 => sourceDir / "scala-2.13-"
    case _                       => sourceDir / "scala-2.13+"
  }
}
