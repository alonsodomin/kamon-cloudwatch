package kamon.cloudwatch

import java.time.Clock
import java.util.concurrent.atomic.AtomicReference

import com.typesafe.config.Config

import kamon.{Kamon, MetricReporter, Tags}
import kamon.metric.PeriodSnapshot

import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global

final case class Configuration(
  nameSpace: String,
  region: Option[String],
  batchSize: Int,
  sendMetrics: Boolean,
  numThreads: Int,
  serviceEndpoint: Option[String],
  includeEnvironmentTags: Boolean
)

object Configuration {

  private object settings {
    val Namespace              = "namespace"
    val BatchSize              = "batch-size"
    val Region                 = "region"
    val SendMetrics            = "send-metrics"
    val NumThreads             = "async-threads"
    val ServiceEndpoint        = "service-endpoint"
    val IncludeEnvironmentTags = "include-environment-tags"
  }

  def fromConfig(config: Config): Configuration = {
    def opt[A](path: String, f: Config => A): Option[A] = {
      if (config.hasPath(path)) Option(f(config))
      else None
    }

    val nameSpace      = config.getString(settings.Namespace)
    val region         = opt(settings.Region, _.getString(settings.Region)).filterNot(_.isEmpty)
    val batchSize      = config.getInt(settings.BatchSize)
    val sendMetrics    = config.getBoolean(settings.SendMetrics)
    val numThreads     = config.getInt(settings.NumThreads)
    val endpoint       = opt(settings.ServiceEndpoint, _.getString(settings.ServiceEndpoint)).filterNot(_.isEmpty)
    val includeEnvTags = opt(settings.IncludeEnvironmentTags, _.getBoolean(settings.IncludeEnvironmentTags)).getOrElse(false)

    Configuration(nameSpace, region, batchSize, sendMetrics, numThreads, endpoint, includeEnvTags)
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
      val metrics = datums(snapshot, CloudWatchReporter.environmentTags(config))
      metrics.grouped(config.batchSize).foreach(batch =>
        shipper.shipMetrics(config.nameSpace, batch)
      )
    }
  }

  private[this] def readConfiguration(config: Config): Configuration = {
    val cloudWatchConfig = config.getConfig("kamon.cloudwatch")
    Configuration.fromConfig(cloudWatchConfig)
  }

}

object CloudWatchReporter {

  private def environmentTags(config: Configuration): Tags = {
    if (config.includeEnvironmentTags) Kamon.environment.tags else Map.empty
  }

}