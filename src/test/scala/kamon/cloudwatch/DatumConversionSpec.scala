package kamon.cloudwatch

import java.time.Instant
import java.util.Date

import com.amazonaws.services.cloudwatch.model.{Dimension, StandardUnit}

import kamon.metric.MeasurementUnit
import kamon.tag.TagSet
import kamon.testkit.MetricSnapshotBuilder

import org.scalatest.{FlatSpec, Matchers}

import scala.collection.JavaConverters._

class DatumConversionSpec extends FlatSpec with Matchers {

  "datums" must "ignore distributions without samples" in {
    val snapshot = PeriodSnapshotBuilder()
      .withHistogram(MetricSnapshotBuilder.histogram("foo", TagSet.Empty)(List.empty[Long]: _*))
      .build()

    val convertedDatums = datums(snapshot, TagSet.Empty)

    convertedDatums shouldBe Vector.empty
  }

  it must "use the 'to' instant as the timestamp of the datum" in {
    val givenInstant = Instant.ofEpochMilli(98439)

    val snapshot = PeriodSnapshotBuilder()
      .to(givenInstant)
      .withCounter(MetricSnapshotBuilder.counter("foo", TagSet.Empty, 2))
      .build()

    val convertedDatums = datums(snapshot, TagSet.Empty)
    convertedDatums.size shouldBe 1
    convertedDatums(0).getTimestamp shouldBe Date.from(givenInstant)
  }

  it must "populate percentages" in {
    val snapshot = PeriodSnapshotBuilder()
      .withCounter(MetricSnapshotBuilder.counter("foo", "", TagSet.Empty, MeasurementUnit.percentage, 39))
      .build()

    val convertedDatums = datums(snapshot, TagSet.Empty)
    convertedDatums.size shouldBe 1
    convertedDatums(0).getUnit shouldBe StandardUnit.Percent.toString
  }

  it must "attach user tags" in {
    val snapshot = PeriodSnapshotBuilder()
      .withCounter(MetricSnapshotBuilder.counter("bar", TagSet.from(Map("tag" -> "tagValue")), 10))
      .build()

    val expectedDimensions = List(
      new Dimension().withName("tag").withValue("tagValue")
    )

    val convertedDatums = datums(snapshot, TagSet.Empty)
    val dimensions = convertedDatums.map(_.getDimensions.asScala).reduceRight(_ ++ _).toList

    dimensions shouldBe expectedDimensions
  }

  it must "attach base (environment) tags" in {
    val snapshot = PeriodSnapshotBuilder()
      .withCounter(MetricSnapshotBuilder.counter("foo", TagSet.Empty, 10))
      .build()

    val expectedDimensions = List(
      new Dimension().withName("quxx").withValue("bar")
    )

    val convertedDatums = datums(snapshot, TagSet.from(Map("quxx" -> "bar")))
    val dimensions = convertedDatums.map(_.getDimensions.asScala).reduceRight(_ ++ _).toList

    dimensions shouldBe expectedDimensions
  }

}
