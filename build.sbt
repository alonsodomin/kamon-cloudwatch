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

val kamonVersion = "1.1.3"
val jacksonVersion = "2.9.7"
val kamonCore    = "io.kamon"               %% "kamon-core"              % kamonVersion
val kamonTestkit = "io.kamon"               %% "kamon-testkit"           % kamonVersion
val cloudwatch   = "com.amazonaws"          %  "aws-java-sdk-cloudwatch" % "1.11.411" excludeAll(
  ExclusionRule("com.fasterxml.jackson.core", "jackson-annotations"),
  ExclusionRule("com.fasterxml.jackson.core", "jackson-core"),
  ExclusionRule("com.fasterxml.jackson.core", "jackson-databind"),
  ExclusionRule("com.fasterxml.jackson.dataformat", "jackson-dataformat-cbor")
)
val jacksonDepdencencies = Seq(
  "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor" % jacksonVersion
)
val wiremock     = "com.github.tomakehurst" %  "wiremock"                % "2.18.0"


libraryDependencies ++=
  compileScope(kamonCore, cloudwatch) ++ compileScope(jacksonDepdencencies: _*) ++
  testScope(scalatest, kamonTestkit, wiremock, slf4jApi, logbackClassic)

resolvers += Resolver.bintrayRepo("kamon-io", "releases")
