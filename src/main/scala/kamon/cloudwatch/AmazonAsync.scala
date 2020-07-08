package kamon.cloudwatch

import java.util.concurrent.{ExecutorService, Executors, Future => JFuture}

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
import scala.util.{Try, Success, Failure}

private[cloudwatch] object AmazonAsync {
  private val logger =
    LoggerFactory.getLogger(classOf[MetricsShipper].getPackage.getName)

  private lazy val DefaultAwsCredentialsProvider: AWSCredentialsProvider =
    new AWSCredentialsProviderChain(
      new EnvironmentVariableCredentialsProvider,
      new ProfileCredentialsProvider(),
      new WebIdentityTokenCredentialsProvider,
      InstanceProfileCredentialsProvider.getInstance(),
      new EC2ContainerCredentialsProviderWrapper
    )

  def buildClient(configuration: Configuration): AmazonCloudWatchAsync = {
    val chosenRegion: Option[Regions] = {
      configuration.region
        .flatMap(r => Try(Regions.fromName(r)).toOption)
        .orElse(Option(Regions.getCurrentRegion).map(r => Regions.fromName(r.getName)))
    }

    val staticCredentialProvider = for {
      accessKey <- configuration.awsAccessKeyId
      secretKey <- configuration.awsSecretKey
    } yield (new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))

    // async aws client uses a thread pool that reuses a fixed number of threads
    // operating off a shared unbounded queue.
    val baseBuilder = AmazonCloudWatchAsyncClientBuilder
      .standard()
      .withCredentials(staticCredentialProvider.getOrElse(DefaultAwsCredentialsProvider))
      .withExecutorFactory(new ExecutorFactory {
        override def newExecutor(): ExecutorService =
          Executors.newFixedThreadPool(configuration.numThreads)
      })

    val endpointConfig = for {
      endpointName <- configuration.serviceEndpoint
      region       <- chosenRegion
    } yield new EndpointConfiguration(endpointName, region.getName)

    endpointConfig
      .map(baseBuilder.withEndpointConfiguration)
      .orElse(chosenRegion.map(baseBuilder.withRegion))
      .getOrElse(baseBuilder)
      .build()
  }

  private def asyncRequest[Req <: AmazonWebServiceRequest, Res <: AmazonWebServiceResult[
    ResponseMetadata
  ]](
      request: Req
  )(send: (Req, AsyncHandler[Req, Res]) => JFuture[Res]): Future[Unit] = {

    val promise: Promise[Unit] = Promise[Unit]
    val handler = new AsyncHandler[Req, Res] {
      override def onError(exception: Exception): Unit =
        promise.failure(exception)

      override def onSuccess(request: Req, res: Res): Unit = {
        val result: Try[Unit] = {
          if (res.getSdkHttpMetadata().getHttpStatusCode() >= 500) {
            Failure(CloudWatchUnavailable)
          } else if (res.getSdkHttpMetadata().getHttpStatusCode() >= 400) {
            Failure(
              InvalidCloudWatchRequest(
                s"Received HTTP status code '${res.getSdkHttpMetadata().getHttpStatusCode()}' from CloudWatch API."
              )
            )
          } else {
            Success(())
          }
        }
        promise.complete(result)
      }
    }

    send(request, handler)
    promise.future
  }

  implicit class MetricsAsyncOps(data: MetricDatumBatch) {

    def put(nameSpace: String)(
        implicit client: AmazonCloudWatchAsync
    ): Future[Unit] = {
      logger.debug("Sending {} metrics to namespace {}.", data.size, nameSpace)
      asyncRequest(
        new PutMetricDataRequest()
          .withNamespace(nameSpace)
          .withMetricData(data.asJava)
      )(client.putMetricDataAsync)
    }

  }
}
