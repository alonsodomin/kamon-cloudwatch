package kamon.cloudwatch

import java.time.{Clock, Instant, ZoneOffset}

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{givenThat => _, _}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit.Stubbing
import com.github.tomakehurst.wiremock.matching.{ContentPattern, RegexPattern}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.JavaConverters._

object CloudWatchReporterSpec {
  final val TestConfig = ConfigFactory.parseString(
    """kamon.cloudwatch {
      |  # namespace is the AWS Metrics custom namespace
      |  namespace = kamon-cloudwatch
      |
      |  # AWS region, on ec2 region is fetched by getCurrentRegion command
      |  region = eu-west-1
      |
      |  # batch size of data when send to Cloudwatch
      |  batch-size = 20
      |
      |  # only logs metrics to file without shipping out to Cloudwatch if it is false
      |  send-metrics = true
      |
      |  # how many threads will be assigned to the pool that does the shipment of metrics
      |  async-threads = 5
      |}""".stripMargin
  )
}

class CloudWatchReporterSpec extends FlatSpec with Matchers {

  val TestClock: Clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)

  def withCloudWatch(config: Config = ConfigFactory.load())(testCode: (Stubbing, CloudWatchReporter) => Any): Unit = {
    val fixture = new Fixture(config)
    try {
      testCode(fixture.cloudWatch, fixture.reporter)
    } finally {
      fixture.reporter.stop()
      fixture.cloudWatch.shutdown()
    }
  }

  "the reporter" should "do something" in withCloudWatch() { (stub, reporter) =>
    val snapshot = new PeriodSnapshotBuilder()
      .counter("foo", Map.empty, 23)
      .build()

    //val serverExpectation = put("/mything").willReturn(aResponse().withStatus(200))

    reporter.reportPeriodSnapshot(snapshot)
    Thread.sleep(2000)

    stub.verify(
      postRequestedFor(urlEqualTo("/"))
        .withRequestBody(cloudWatchBody(
          "Action"                 -> "PutMetricData",
          "Namespace"                      -> "kamon-cloudwatch",
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
        "kamon.cloudwatch.service-endpoint" -> s"http://localhost:${cloudWatch.port()}"
      ).asJava)

      val r = new CloudWatchReporter(TestClock)
      r.reconfigure(endpoint.withFallback(config))
      r
    }

  }

  def cloudWatchBody(keys: (String, String)*): CloudWatchBodyPattern =
    new CloudWatchBodyPattern(Map(keys: _*))

  class CloudWatchBodyPattern(params: Map[String, String])
    extends RegexPattern(params.map { case (k,v) => s"$k=$v"}.mkString("(.*?)", "(.*?)", "(.*?)"))

}
