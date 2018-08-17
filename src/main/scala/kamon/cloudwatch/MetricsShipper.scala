package kamon.cloudwatch

import java.util.concurrent.{ExecutorService, Executors}
import java.util.concurrent.atomic.AtomicReference

import com.amazonaws.auth._
import com.amazonaws.auth.profile._
import com.amazonaws.client.builder.ExecutorFactory
import com.amazonaws.regions.Regions
import com.amazonaws.services.cloudwatch.{AmazonCloudWatchAsync, AmazonCloudWatchAsyncClientBuilder}
import kamon.cloudwatch.AmazonAsync.{MetricDatumBatch, MetricsAsyncOps}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

/**
  * Ship-and-forget. Let the future to process the actual shipment to Cloudwatch.
  */

private[cloudwatch] object MetricsShipper {
    private[CloudWatchReporter] val DefaultAwsCredentialsProvider: AWSCredentialsProvider = new AWSCredentialsProviderChain(
      new EnvironmentVariableCredentialsProvider,
      new ProfileCredentialsProvider(),
      InstanceProfileCredentialsProvider.getInstance(),
      new EC2ContainerCredentialsProviderWrapper
    )
}
private[cloudwatch] class MetricsShipper {
  private val logger = LoggerFactory.getLogger(classOf[MetricsShipper])

  // Kamon 1.0 requires to support hot-reconfiguration, which forces us to use an
  // AtomicReference here and hope for the best
  private val client: AtomicReference[AmazonCloudWatchAsync] = new AtomicReference()

  def reconfigure(configuration: Configuration): Unit = {
    def chosenRegion: Option[Regions] = {
      configuration.region
        .flatMap(r => Try(Regions.fromName(r)).toOption)
        .orElse(Option(Regions.getCurrentRegion).map(r => Regions.fromName(r.getName)))
    }

    // async aws client uses a thread pool that reuses a fixed number of threads
    // operating off a shared unbounded queue.
    def clientFromConfig: AmazonCloudWatchAsync = {
      val baseBuilder = AmazonCloudWatchAsyncClientBuilder.standard()
          .withExecutorFactory(new ExecutorFactory {
            override def newExecutor(): ExecutorService =
              Executors.newFixedThreadPool(configuration.numThreads)
          })

      chosenRegion.fold(baseBuilder)(baseBuilder.withRegion).build()
    }

    val oldClient = client.getAndSet(clientFromConfig)
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
