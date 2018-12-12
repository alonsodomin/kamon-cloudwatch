package kamon.cloudwatch

import java.time.Instant
import java.util.Date

import com.amazonaws.services.cloudwatch.model.{Dimension, StandardUnit}
import kamon.metric.MeasurementUnit

import org.scalatest.{FlatSpec, Matchers}

import scala.collection.JavaConverters._

class DatumConversionSpec extends FlatSpec with Matchers {

  "datums" must "ignore distributions without samples" in {
    val snapshot = PeriodSnapshotBuilder()
      .rangeSampler("foo", Map.empty)
      .build()

    val convertedDatums = datums(snapshot, Map.empty)

    convertedDatums shouldBe Vector.empty
  }

  it must "use the 'to' instant as the timestamp of the datum" in {
    val givenInstant = Instant.ofEpochMilli(98439)

    val snapshot = PeriodSnapshotBuilder()
      .to(givenInstant)
      .counter("foo", Map.empty, 2)
      .build()

    val convertedDatums = datums(snapshot, Map.empty)
    convertedDatums.size shouldBe 1
    convertedDatums(0).getTimestamp shouldBe Date.from(givenInstant)
  }

  it must "populate percentages" in {
    val snapshot = PeriodSnapshotBuilder()
      .counter("foo", Map.empty, 39, MeasurementUnit.percentage)
      .build()

    val convertedDatums = datums(snapshot, Map.empty)
    convertedDatums.size shouldBe 1
    convertedDatums(0).getUnit shouldBe StandardUnit.Percent.toString
  }

  it must "attach user tags" in {
    val snapshot = PeriodSnapshotBuilder()
      .counter("bar", Map("tag" -> "tagValue"), 10)
      .build()

    val expectedDimensions = List(
      new Dimension().withName("tag").withValue("tagValue")
    )

    val convertedDatums = datums(snapshot, Map.empty)
    val dimensions = convertedDatums.map(_.getDimensions.asScala).reduceRight(_ ++ _).toList

    dimensions shouldBe expectedDimensions
  }

  it must "attach base (environment) tags" in {
    val snapshot = PeriodSnapshotBuilder()
      .counter("foo", Map.empty, 10)
      .build()

    val expectedDimensions = List(
      new Dimension().withName("quxx").withValue("bar")
    )

    val convertedDatums = datums(snapshot, Map("quxx" -> "bar"))
    val dimensions = convertedDatums.map(_.getDimensions.asScala).reduceRight(_ ++ _).toList

    dimensions shouldBe expectedDimensions
  }

}
