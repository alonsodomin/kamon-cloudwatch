package kamon.cloudwatch

import java.time.Instant

import kamon.Kamon
import kamon.metric._
import kamon.testkit.MetricInspection

class PeriodSnapshotBuilder extends MetricInspection {
  private var _from: Instant = Instant.ofEpochSecond(1)
  private var _to: Instant = Instant.ofEpochSecond(2)
  private var _counters: Seq[MetricValue] = Seq.empty
  private var _gauges: Seq[MetricValue] = Seq.empty
  private var _histograms: Seq[MetricDistribution] = Seq.empty
  private var _rangeSamplers: Seq[MetricDistribution] = Seq.empty

  def from(instant: Instant): PeriodSnapshotBuilder = {
    this._from = instant
    this
  }

  def to(instant: Instant): PeriodSnapshotBuilder = {
    this._to = instant
    this
  }

  def counter(name: String, tags: Map[String, String], value: Long): PeriodSnapshotBuilder = {
    _counters = _counters :+ MetricValue(name, tags, MeasurementUnit.none, value)
    this
  }

  def gauge(name: String, tags: Map[String, String], value: Long): PeriodSnapshotBuilder = {
    _gauges = _gauges :+ MetricValue(name, tags, MeasurementUnit.none, value)
    this
  }

  def histogram(name: String, tags: Map[String, String], values: Long*): PeriodSnapshotBuilder = {
    val temp = Kamon.histogram("temp")
    values.foreach(v => temp.record(v))
    _histograms = _histograms :+ MetricDistribution(name, tags, MeasurementUnit.time.nanoseconds, DynamicRange.Default, temp.distribution())
    this
  }

  def rangeSampler(name: String, tags: Map[String, String], values: Long*): PeriodSnapshotBuilder = {
    val temp = Kamon.histogram("temp")
    values.foreach(v => temp.record(v))
    _rangeSamplers = _rangeSamplers :+ MetricDistribution(name, tags, MeasurementUnit.time.nanoseconds, DynamicRange.Default, temp.distribution())
    this
  }


  def build(): PeriodSnapshot =
    PeriodSnapshot(_from, _to, MetricsSnapshot(_histograms, _rangeSamplers, _gauges, _counters))

}
