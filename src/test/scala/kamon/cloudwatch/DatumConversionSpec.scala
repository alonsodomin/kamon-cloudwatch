package kamon.cloudwatch

import java.time.Clock

import org.scalatest.{FlatSpec, Matchers}

class DatumConversionSpec extends FlatSpec with Matchers {

  "Distributions with 0 samples" must "be igonored" in {
    val snapshot = PeriodSnapshotBuilder()
      .rangeSampler("foo", Map.empty)
      .build()

    val convertedDatums = datums(snapshot)(Clock.systemUTC())

    convertedDatums shouldBe Vector.empty
  }

}
