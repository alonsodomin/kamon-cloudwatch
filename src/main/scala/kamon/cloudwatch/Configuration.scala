package kamon.cloudwatch

import com.typesafe.config.Config

final case class Configuration(
    nameSpace: String,
    region: Option[String],
    batchSize: Int,
    numThreads: Int,
    serviceEndpoint: Option[String],
    includeEnvironmentTags: Boolean,
    awsAccessKeyId: Option[String],
    awsSecretKey: Option[String]
)

object Configuration {
  final val Namespace = "kamon.cloudwatch"

  private object settings {
    val Namespace              = "namespace"
    val BatchSize              = "batch-size"
    val Region                 = "region"
    val AwsAccessKeyId         = "access-key-id"
    val AwsSecretAccessKey     = "secret-access-key"
    val NumThreads             = "async-threads"
    val ServiceEndpoint        = "service-endpoint"
    val IncludeEnvironmentTags = "include-environment-tags"
  }

  def fromConfig(topLevelCfg: Config): Configuration = {
    val config = topLevelCfg.getConfig(Namespace)

    def opt[A](path: String, f: Config => String => A): Option[A] =
      if (config.hasPath(path)) Option(f(config)(path))
      else None

    val nameSpace      = config.getString(settings.Namespace)
    val region         = opt(settings.Region, _.getString).filterNot(_.isEmpty)
    val awsAccessKeyId = opt(settings.AwsAccessKeyId, _.getString).filterNot(_.isEmpty)
    val awsSecretKey   = opt(settings.AwsSecretAccessKey, _.getString).filterNot(_.isEmpty)
    val batchSize      = config.getInt(settings.BatchSize)
    val numThreads     = config.getInt(settings.NumThreads)
    val endpoint       = opt(settings.ServiceEndpoint, _.getString).filterNot(_.isEmpty)
    val includeEnvTags = opt(settings.IncludeEnvironmentTags, _.getBoolean).getOrElse(false)

    Configuration(
      nameSpace,
      region,
      batchSize,
      numThreads,
      endpoint,
      includeEnvTags,
      awsAccessKeyId,
      awsSecretKey
    )
  }

}
