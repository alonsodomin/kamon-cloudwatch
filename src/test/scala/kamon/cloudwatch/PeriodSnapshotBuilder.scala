package kamon.cloudwatch

import java.time.{Duration, Instant}

import kamon.Kamon
import kamon.metric._
import kamon.tag.TagSet
import kamon.testkit.MetricInspection

class PeriodSnapshotBuilder {

  private var _from: Instant = Instant.ofEpochSecond(1)
  private var _to: Instant = Instant.ofEpochSecond(2)
  private var _counters: Vector[MetricSnapshot.Values[Long]] = Vector.empty
  private var _gauges: Vector[MetricSnapshot.Values[Double]] = Vector.empty
  private var _histograms: Vector[MetricSnapshot.Distributions] = Vector.empty
  private var _timers: Vector[MetricSnapshot.Distributions] = Vector.empty
  private var _rangeSamplers: Vector[MetricSnapshot.Distributions] = Vector.empty

  def from(instant: Instant): PeriodSnapshotBuilder = {
    this._from = instant
    this
  }

  def to(instant: Instant): PeriodSnapshotBuilder = {
    this._to = instant
    this
  }

  def withCounter(counter: MetricSnapshot.Values[Long]): PeriodSnapshotBuilder = {
    _counters = _counters :+ counter
    this
  }

  def withGauge(gauge: MetricSnapshot.Values[Double]): PeriodSnapshotBuilder = {
    _gauges = _gauges :+ gauge
    this
  }

  def withHistogram(histogram: MetricSnapshot.Distributions): PeriodSnapshotBuilder = {
    _histograms = _histograms :+ histogram
    this
  }

  def withRangeSampler(sampler: MetricSnapshot.Distributions): PeriodSnapshotBuilder = {
    _rangeSamplers = _rangeSamplers :+ sampler
    this
  }


  def build(): PeriodSnapshot =
    PeriodSnapshot(_from, _to, _counters, _gauges, _histograms, _timers, _rangeSamplers)

}

object PeriodSnapshotBuilder {
  def apply(): PeriodSnapshotBuilder = new PeriodSnapshotBuilder
}
