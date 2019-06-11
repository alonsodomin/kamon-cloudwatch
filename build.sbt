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

disablePlugins(BintrayPlugin)

organization := "com.github.alonsodomin"
bintrayOrganization := None

publishTo := Some(
  if (isSnapshot.value) Opts.resolver.sonatypeSnapshots
  else Opts.resolver.sonatypeStaging
)

pomExtra := 
  <url>https://www.github.com/alonsodomin/kamon-cloudwatch</url>
  <scm>
    <url>git://github.com/alonsodomin/kamon-cloudwatch.git</url>
    <connection>scm:git:git@github.com:alonsodomin/kamon-cloudwatch.git</connection>
  </scm>
  <developers>
    <developer>
      <id>alonsodomin</id>
      <name>A. Alonso Dominguez</name>
      <url>https://github.com/alonsodomin</url>
    </developer>
  </developers>

val kamonVersion   = "1.1.3"
val jacksonVersion = "2.9.6"
val kamonCore      = "io.kamon"               %% "kamon-core"              % kamonVersion
val kamonTestkit   = "io.kamon"               %% "kamon-testkit"           % kamonVersion
val cloudwatch     = "com.amazonaws"          %  "aws-java-sdk-cloudwatch" % "1.11.411" 
val wiremock       = "com.github.tomakehurst" %  "wiremock"                % "2.18.0"


libraryDependencies ++=
  compileScope(kamonCore, cloudwatch) ++
  testScope(scalatest, kamonTestkit, wiremock, slf4jApi, logbackClassic)

resolvers += Resolver.bintrayRepo("kamon-io", "releases")
