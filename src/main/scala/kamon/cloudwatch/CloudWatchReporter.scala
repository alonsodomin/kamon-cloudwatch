package kamon.cloudwatch

import java.time.{Clock, Instant}
import java.util.Date
import java.util.concurrent.atomic.AtomicReference

import com.typesafe.config.Config
import com.amazonaws.services.cloudwatch.model._

import kamon.{Kamon, MetricReporter, Tags}
import kamon.metric.{MeasurementUnit, MetricDistribution, MetricValue, PeriodSnapshot}
import kamon.cloudwatch.AmazonAsync.MetricDatumBatch

import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global

final case class Configuration(
  nameSpace: String,
  region: Option[String],
  batchSize: Int,
  sendMetrics: Boolean,
  numThreads: Int,
  serviceEndpoint: Option[String]
)

object Configuration {

  private object settings {
    val Namespace       = "namespace"
    val BatchSize       = "batch-size"
    val Region          = "region"
    val SendMetrics     = "send-metrics"
    val NumThreads      = "async-threads"
    val ServiceEndpoint = "service-endpoint"
  }

  def fromConfig(config: Config): Configuration = {
    def opt[A](path: String, f: Config => A): Option[A] = {
      if (config.hasPath(path)) Option(f(config))
      else None
    }

    val nameSpace   = config.getString(settings.Namespace)
    val region      = opt(settings.Region, _.getString(settings.Region)).filterNot(_.isEmpty)
    val batchSize   = config.getInt(settings.BatchSize)
    val sendMetrics = config.getBoolean(settings.SendMetrics)
    val numThreads  = config.getInt(settings.NumThreads)
    val endpoint    = opt(settings.ServiceEndpoint, _.getString(settings.ServiceEndpoint)).filterNot(_.isEmpty)

    Configuration(nameSpace, region, batchSize, sendMetrics, numThreads, endpoint)
  }

}

class CloudWatchReporter private[cloudwatch] (clock: Clock) extends MetricReporter {
  private val logger = LoggerFactory.getLogger(classOf[MetricsShipper].getPackage.getName)

  def this() = this(Clock.systemUTC())

  private[this] val configuration: AtomicReference[Configuration] =
    new AtomicReference()

  private[this] val shipper: MetricsShipper = new MetricsShipper()

  override def start(): Unit = {
    logger.info("Starting the Kamon CloudWatch reporter.")
    configuration.set(readConfiguration(Kamon.config()))
    shipper.reconfigure(configuration.get())
  }

  override def stop(): Unit = {
    logger.info("Shutting down the Kamon CloudWatch reporter.")
    shipper.shutdown()
  }

  override def reconfigure(config: Config): Unit = {
    val current = configuration.get
    if (configuration.compareAndSet(current, readConfiguration(config))) {
      shipper.reconfigure(configuration.get)
      logger.info("Configuration reloaded successfully.")
    } else {
      logger.debug("Configuration hasn't changed from the last reload")
    }
  }

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    val config = configuration.get

    if (config.sendMetrics) {
      val metrics = datums(snapshot)
      metrics.grouped(config.batchSize).foreach(batch =>
        shipper.shipMetrics(config.nameSpace, batch)
      )
    }
  }

  private[this] def readConfiguration(config: Config): Configuration = {
    val cloudWatchConfig = config.getConfig("kamon.cloudwatch")
    Configuration.fromConfig(cloudWatchConfig)
  }

  /**
    * Produce the datums.
    * Code is take from:
    * https://github.com/philwill-nap/Kamon/blob/master/kamon-cloudwatch/
    * src/main/scala/kamon/cloudwatch/CloudWatchMetricsSender.scala
    */
  private def datums(snapshot: PeriodSnapshot): MetricDatumBatch = {
    def unitAndScale(unit: MeasurementUnit): (StandardUnit, Double) = {
      import MeasurementUnit.Dimension._
      import MeasurementUnit.{information, time}

      unit.dimension match {
        case Time if unit.magnitude == time.seconds.magnitude =>
          StandardUnit.Seconds -> 1.0
        case Time if unit.magnitude == time.milliseconds.magnitude =>
          StandardUnit.Milliseconds -> 1.0
        case Time if unit.magnitude == time.microseconds.magnitude =>
          StandardUnit.Microseconds -> 1.0
        case Time if unit.magnitude == time.nanoseconds.magnitude =>
          StandardUnit.Microseconds -> 1E-3

        case Information if unit.magnitude == information.bytes.magnitude =>
          StandardUnit.Bytes -> 1.0
        case Information if unit.magnitude == information.kilobytes.magnitude =>
          StandardUnit.Kilobytes -> 1.0
        case Information if unit.magnitude == information.megabytes.magnitude =>
          StandardUnit.Megabytes -> 1.0
        case Information if unit.magnitude == information.gigabytes.magnitude =>
          StandardUnit.Gigabytes -> 1.0

        case _ => StandardUnit.Count -> 1.0
      }
    }

    def datum(name: String, tags: Tags, unit: StandardUnit): MetricDatum = {
      val dimensions: List[Dimension] =
        tags.map {
          case (tagName, tagValue) => new Dimension().withName(tagName).withValue(tagValue)
        }.toList

      new MetricDatum()
        .withDimensions(dimensions.asJava)
        .withMetricName(name)
        .withTimestamp(Date.from(Instant.now(clock)))
        .withUnit(unit)
    }

    def datumFromDistribution(metric: MetricDistribution): MetricDatum = {
      val (unit, scale) = unitAndScale(metric.unit)
      val statSet = new StatisticSet()
        .withMaximum(metric.distribution.max.toDouble * scale)
        .withMinimum(metric.distribution.min.toDouble * scale)
        .withSampleCount(metric.distribution.count.toDouble)
        .withSum(metric.distribution.sum.toDouble * scale)

      datum(metric.name, metric.tags, unit)
        .withStatisticValues(statSet)
    }

    def datumFromValue(metric: MetricValue): MetricDatum = {
      val (unit, scale) = unitAndScale(metric.unit)
      datum(metric.name, metric.tags, unit)
        .withValue(metric.value.toDouble * scale)
    }

    val allDatums =
      snapshot.metrics.histograms.view.map(datumFromDistribution) ++
      snapshot.metrics.rangeSamplers.map(datumFromDistribution) ++
      snapshot.metrics.gauges.view.map(datumFromValue) ++
      snapshot.metrics.counters.view.map(datumFromValue)

    allDatums.toVector
  }
}