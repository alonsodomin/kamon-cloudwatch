package kamon.cloudwatch

import java.time.{Clock, Instant, ZoneOffset}

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{givenThat => _, _}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit.Stubbing
import com.github.tomakehurst.wiremock.matching.{StringValuePattern, MatchResult}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.JavaConverters._

object CloudWatchReporterSpec {
  final val TestClock: Clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)

  final val TestConfig = ConfigFactory.parseString(
    """kamon.cloudwatch {
      |  namespace = kamon-cloudwatch-test
      |  batch-size = 20
      |  send-metrics = true
      |  async-threads = 5
      |}""".stripMargin
  )
}

class CloudWatchReporterSpec extends FlatSpec with Matchers {
  import CloudWatchReporterSpec._

  def withCloudWatch(config: Config = ConfigFactory.load())(testCode: (Stubbing, CloudWatchReporter) => Any): Unit = {
    val fixture = new Fixture(config)
    try {
      testCode(fixture.cloudWatch, fixture.reporter)
    } finally {
      fixture.reporter.stop()
      fixture.cloudWatch.shutdown()
    }
  }

  "the reporter" should "publish metrics" in withCloudWatch(TestConfig) { (stub, reporter) =>
    val snapshot = new PeriodSnapshotBuilder()
      .counter("foo", Map("tag" -> "mytag"), 23)
      .build()

    reporter.reportPeriodSnapshot(snapshot)
    Thread.sleep(2500)

    stub.verify(
      postRequestedFor(urlEqualTo("/"))
        .withRequestBody(cloudWatchBody(
          "Action"                         -> "PutMetricData",
          "Namespace"                      -> "kamon-cloudwatch-test",
          "MetricData.member.1.MetricName" -> "foo",
          "MetricData.member.1.Value"      -> "23.0",
          "MetricData.member.1.Unit"       -> "Count"
        ))
    )
  }

  class Fixture(config: Config) {

    val cloudWatch = {
      val server = new WireMockServer(WireMockConfiguration.wireMockConfig())
      server.start()
      server
    }

    val reporter: CloudWatchReporter = {
      val endpoint = ConfigFactory.parseMap(Map(
        "kamon.cloudwatch.service-endpoint" -> s"http://localhost:${cloudWatch.port()}",
        "kamon.cloudwatch.region"           -> "us-west-1"
      ).asJava)

      val r = new CloudWatchReporter(TestClock)
      r.reconfigure(endpoint.withFallback(config))
      r
    }

  }

  def cloudWatchBody(keys: (String, String)*): CloudWatchBodyPattern =
    new CloudWatchBodyPattern(Map(keys: _*))

  class CloudWatchBodyPattern(params: Map[String, String])
    extends StringValuePattern(params.map { case (k,v) => s"$k=$v"}.mkString("&")) {

      override def `match`(body: String): MatchResult = {
        def keyValue(expr: String): Option[(String, String)] = {
          val pairs = expr.split("=")
          if (pairs.size != 2) None
          else Some(pairs(0) -> pairs(1))
        }
        val keyValuePairs = body.split("&").flatMap(keyValue).toMap

        println(keyValuePairs)

        MatchResult.of(params.forall { case (k,v) => keyValuePairs.get(k).fold(false)(_ == v) })
      }

    }

}
