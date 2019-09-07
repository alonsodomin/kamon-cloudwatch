package kamon

import java.util.Date

import com.amazonaws.services.cloudwatch.model.{Dimension, MetricDatum, StandardUnit, StatisticSet}

import kamon.metric.{Distribution, MeasurementUnit, Metric, MetricSnapshot, PeriodSnapshot}
import kamon.tag.{Tag, TagSet}

import scala.jdk.CollectionConverters._

package object cloudwatch {

  type MetricDatumBatch = Vector[MetricDatum]

  /**
    * Produce the datums.
    * Code is take from:
    * https://github.com/philwill-nap/Kamon/blob/master/kamon-cloudwatch/
    * src/main/scala/kamon/cloudwatch/CloudWatchMetricsSender.scala
    */
  private[cloudwatch] def datums(snapshot: PeriodSnapshot, baseTags: TagSet): MetricDatumBatch = {
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

    def datum(name: String, tags: TagSet, unit: StandardUnit): MetricDatum = {
      val dimensions: List[Dimension] =
        (baseTags withTags tags).iterator().map { tag =>
          new Dimension().withName(tag.key).withValue(Tag.unwrapValue(tag).toString)
        }.toList

      val baseDatum = new MetricDatum()
        .withMetricName(name)
        .withTimestamp(Date.from(snapshot.to))
        .withUnit(unit)

      if (dimensions.nonEmpty) {
        baseDatum.withDimensions(dimensions.asJava)
      } else baseDatum
    }

    def datumFromDistribution(distSnap: MetricSnapshot[Metric.Settings.ForDistributionInstrument, Distribution]): Seq[MetricDatum] = {
      val (unit, scale) = unitAndScale(distSnap.settings.unit)
      distSnap.instruments.filter(_.value.count > 0).map { snap =>
        val statSet = new StatisticSet()
          .withMaximum(snap.value.max.toDouble * scale)
          .withMinimum(snap.value.min.toDouble * scale)
          .withSampleCount(snap.value.count.toDouble)
          .withSum(snap.value.sum.toDouble * scale)

        datum(distSnap.name, snap.tags, unit).withStatisticValues(statSet)
      }
    }

    def datumFromValue[T](valueSnap: MetricSnapshot[Metric.Settings.ForValueInstrument, T])(implicit T: Numeric[T]): Seq[MetricDatum] = {
      val (unit, scale) = unitAndScale(valueSnap.settings.unit)

      valueSnap.instruments.map { snap =>
        datum(valueSnap.name, snap.tags, unit)
          .withValue(T.toDouble(snap.value) * scale)
      }
    }

    val allDatums =
      snapshot.histograms.view.flatMap(datumFromDistribution) ++
      snapshot.rangeSamplers.flatMap(datumFromDistribution) ++
      snapshot.gauges.view.flatMap(datumFromValue[Double]) ++
      snapshot.counters.view.flatMap(datumFromValue[Long])

    allDatums.toVector
  }

}
