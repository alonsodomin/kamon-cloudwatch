package kamon.cloudwatch

import java.util.concurrent.atomic.AtomicReference

import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsync

import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/**
  * Ship-and-forget. Let the future to process the actual shipment to Cloudwatch.
  */
private[cloudwatch] class MetricsShipper {
  import AmazonAsync._
  private val logger = LoggerFactory.getLogger(classOf[MetricsShipper])

  // Kamon 1.0 requires to support hot-reconfiguration, which forces us to use an
  // AtomicReference here and hope for the best
  private val client: AtomicReference[AmazonCloudWatchAsync] = new AtomicReference()

  def reconfigure(configuration: Configuration): Unit = {
    val oldClient = client.getAndSet(AmazonAsync.buildClient(configuration))
    if (oldClient != null) {
      disposeClient(oldClient)
    }
  }

  def shutdown(): Unit = {
    val oldClient = client.getAndSet(null)
    if (oldClient != null) {
      disposeClient(oldClient)
    }
  }

  def shipMetrics(nameSpace: String, datums: MetricDatumBatch)(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val currentClient: AmazonCloudWatchAsync = client.get
    datums.put(nameSpace)
      .map(result => logger.debug(s"Succeeded to push metrics to Cloudwatch: $result"))
      .recover {
        case error: Exception =>
          logger.warn(s"Failed to send metrics to Cloudwatch ${error.getMessage}")
      }
  }

  private[this] def disposeClient(client: AmazonCloudWatchAsync): Unit = {
    try {
      client.shutdown()
    } catch {
      case NonFatal(_) => // ignore exception
    }
  }

}
