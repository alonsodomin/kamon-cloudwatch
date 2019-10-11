package kamon.cloudwatch

import java.time.Clock
import java.util.concurrent.atomic.AtomicReference

import com.typesafe.config.Config

import kamon.Kamon
import kamon.module.{ModuleFactory, MetricReporter}
import kamon.metric.PeriodSnapshot
import kamon.tag.TagSet

import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Success, Failure}

final class CloudWatchModuleFactory extends ModuleFactory {
  private[this] val logger = LoggerFactory.getLogger(classOf[MetricsShipper].getPackage.getName)

  override def create(settings: ModuleFactory.Settings): CloudWatchReporter = {
    logger.info("Starting the Kamon CloudWatch reporter.")
    val cfg = Configuration.fromConfig(settings.config)
    new CloudWatchReporter(cfg)
  }
}

final class CloudWatchReporter private[cloudwatch] (cfg: Configuration, clock: Clock)
    extends MetricReporter {
  import CloudWatchReporter._

  private[this] val logger = LoggerFactory.getLogger(classOf[MetricsShipper].getPackage.getName)

  def this(cfg: Configuration) = this(cfg, Clock.systemUTC())

  private[this] val configuration: AtomicReference[Configuration] =
    new AtomicReference(cfg)

  private[this] val shipper: MetricsShipper = new MetricsShipper(cfg)

  override def stop(): Unit = {
    logger.info("Shutting down the Kamon CloudWatch reporter.")
    shipper.shutdown()
  }

  override def reconfigure(config: Config): Unit = {
    val current = configuration.get
    if (configuration.compareAndSet(current, Configuration.fromConfig(config))) {
      shipper.reconfigure(configuration.get)
      logger.info("Configuration reloaded successfully.")
    } else {
      logger.debug("Configuration hasn't changed from the last reload")
    }
  }

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    val config = configuration.get
    val metrics = datums(
      snapshot,
      CloudWatchReporter.environmentTags(config)
    ).grouped(config.batchSize)

    Future.traverse(metrics)(shipper.shipMetrics(config.nameSpace, _)).onComplete {
      case Success(_) =>
        logger.debug("Metrics shipment has completed successfully.")

      case Failure(exception) =>
        logger.warn("Could not ship metrics to CloudWatch", exception)
    }
  }

}

object CloudWatchReporter {

  private def environmentTags(config: Configuration): TagSet =
    if (config.includeEnvironmentTags) Kamon.environment.tags else TagSet.Empty

}
