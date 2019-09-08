package kamon.cloudwatch

import java.util.concurrent.{CancellationException, ExecutorService, Executors, Future => JFuture}

import com.amazonaws.{AmazonWebServiceRequest, AmazonWebServiceResult, ResponseMetadata}
import com.amazonaws.auth._
import com.amazonaws.auth.profile._
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.client.builder.ExecutorFactory
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.regions.Regions
import com.amazonaws.services.cloudwatch.{AmazonCloudWatchAsync, AmazonCloudWatchAsyncClientBuilder}
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest

import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters._
import scala.concurrent.{Future, Promise}
import scala.util.Try

private[cloudwatch] object AmazonAsync {
  private val logger = LoggerFactory.getLogger(classOf[MetricsShipper].getPackage.getName)

  private val DefaultAwsCredentialsProvider: AWSCredentialsProvider = new AWSCredentialsProviderChain(
    new EnvironmentVariableCredentialsProvider,
    new ProfileCredentialsProvider(),
    InstanceProfileCredentialsProvider.getInstance(),
    new EC2ContainerCredentialsProviderWrapper
  )

  def buildClient(configuration: Configuration): AmazonCloudWatchAsync = {
    val chosenRegion: Option[Regions] = {
      configuration.region
        .flatMap(r => Try(Regions.fromName(r)).toOption)
        .orElse(Option(Regions.getCurrentRegion).map(r => Regions.fromName(r.getName)))
    }

    // async aws client uses a thread pool that reuses a fixed number of threads
    // operating off a shared unbounded queue.
    val baseBuilder = AmazonCloudWatchAsyncClientBuilder.standard()
      .withCredentials(DefaultAwsCredentialsProvider)
      .withExecutorFactory(new ExecutorFactory {
        override def newExecutor(): ExecutorService =
          Executors.newFixedThreadPool(configuration.numThreads)
      })

    val endpointConfig = for {
      endpointName <- configuration.serviceEndpoint
      region       <- chosenRegion
    } yield new EndpointConfiguration(endpointName, region.getName)

    endpointConfig.map(baseBuilder.withEndpointConfiguration)
      .orElse(chosenRegion.map(baseBuilder.withRegion))
      .getOrElse(baseBuilder)
      .build()
  }

  private def asyncRequest[Arg, Req <: AmazonWebServiceRequest, Res](asyncArg: Arg)
      (asyncOp: (Arg, AsyncHandler[Req, Res]) => JFuture[Res]): Future[Res] = {

    val promise: Promise[Res] = Promise[Res]
    val handler = new AsyncHandler[Req, Res] {
      override def onError(exception: Exception): Unit =
        promise.failure(new CancellationException(s"AWS async command is cancelled: ${exception.getMessage}"))
      override def onSuccess(request: Req, result: Res): Unit = promise.complete(Try(result))
    }
    asyncOp(asyncArg, handler)
    promise.future
  }

  implicit class MetricsAsyncOps(data: MetricDatumBatch) {

    def put(nameSpace: String)(
      implicit client: AmazonCloudWatchAsync
    ): Future[AmazonWebServiceResult[ResponseMetadata]] = {
      logger.debug("Sending {} metrics to namespace {}.", data.size, nameSpace)
      asyncRequest(new PutMetricDataRequest()
        .withNamespace(nameSpace)
        .withMetricData(data.asJava)
      )(client.putMetricDataAsync)
    }

  }
}
