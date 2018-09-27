package kamon

import java.util.Date

import com.amazonaws.services.cloudwatch.model.{Dimension, MetricDatum, StandardUnit, StatisticSet}

import kamon.metric.{MeasurementUnit, MetricDistribution, MetricValue, PeriodSnapshot}

import scala.collection.JavaConverters._

package object cloudwatch {

  type MetricDatumBatch = Vector[MetricDatum]

  /**
    * Produce the datums.
    * Code is take from:
    * https://github.com/philwill-nap/Kamon/blob/master/kamon-cloudwatch/
    * src/main/scala/kamon/cloudwatch/CloudWatchMetricsSender.scala
    */
  private[cloudwatch] def datums(snapshot: PeriodSnapshot): MetricDatumBatch = {
    def unitAndScale(unit: MeasurementUnit): (StandardUnit, Double) = {
      import MeasurementUnit.Dimension._
      import MeasurementUnit.{information, time}

      unit.dimension match {
        case Percentage => StandardUnit.Percent -> 1.0

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
        .withTimestamp(Date.from(snapshot.to))
        .withUnit(unit)
    }

    def datumFromDistribution(metric: MetricDistribution): Option[MetricDatum] = {
      if (metric.distribution.count == 0) None
      else Some {
        val (unit, scale) = unitAndScale(metric.unit)
        val statSet = new StatisticSet()
          .withMaximum(metric.distribution.max.toDouble * scale)
          .withMinimum(metric.distribution.min.toDouble * scale)
          .withSampleCount(metric.distribution.count.toDouble)
          .withSum(metric.distribution.sum.toDouble * scale)

        datum(metric.name, metric.tags, unit)
          .withStatisticValues(statSet)
      }
    }

    def datumFromValue(metric: MetricValue): MetricDatum = {
      val (unit, scale) = unitAndScale(metric.unit)
      datum(metric.name, metric.tags, unit)
        .withValue(metric.value.toDouble * scale)
    }

    val allDatums =
      snapshot.metrics.histograms.view.flatMap(datumFromDistribution) ++
      snapshot.metrics.rangeSamplers.flatMap(datumFromDistribution) ++
      snapshot.metrics.gauges.view.map(datumFromValue) ++
      snapshot.metrics.counters.view.map(datumFromValue)

    allDatums.toVector
  }

}
