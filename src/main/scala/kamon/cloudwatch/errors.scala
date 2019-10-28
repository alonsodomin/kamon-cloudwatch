package kamon.cloudwatch

sealed abstract class CloudwatchException(msg: String) extends Exception(msg)

final case object CloudWatchUnavailable extends CloudwatchException("CloudWatch service is not available")

final case class InvalidCloudWatchRequest(msg: String) extends CloudwatchException(msg)
