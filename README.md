# Kamon AWS Cloudwatch Extension

[![Build Status](https://travis-ci.org/alonsodomin/kamon-cloudwatch.svg?branch=master)](https://travis-ci.org/alonsodomin/kamon-cloudwatch)
[![GitHub release](https://img.shields.io/github/tag/alonsodomin/kamon-cloudwatch.svg)](https://github.com/alonsodomin/kamon-cloudwatch/releases)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.alonsodomin/kamon-cloudwatch/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.alonsodomin/kamon-cloudwatch)

## Overview

A simple [Kamon](https://github.com/kamon-io/Kamon) extension to ship metrics data to Cloudwatch using Amazon's async client.

_**Note:** This project has been initially forked from [Timeout's kamon-cloudwatch](https://github.com/timeoutdigital/kamon-cloudwatch) but evolved separately as the original one has fallen out of maintenance._

## Installation
- add library dependency to your build.sbt

```scala
libraryDependencies += "com.github.alonsodomin" %% "kamon-cloudwatch" % "1.0.0"
```

- load the reporter by Kamon

```scala
val reporter = new CloudWatchReporter()
Kamon.addReporter(reporter)
```

- make sure you have `AWS_PROFILE` or `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` pair set correctly.

- add the following to your application.conf and change the fields accordingly:

```
kamon {
  cloudwatch {

    # namespace is the AWS Metrics custom namespace
    namespace = kamon-cloudwatch
    
    # AWS region, on ec2 region is fetched by getCurrentRegion command
    region = eu-west-1

    # batch size of data when send to Cloudwatch    
    batch-size = 20

    # only logs metrics to file without shipping out to Cloudwatch if it is false
    send-metrics = true

    # how many threads will be assigned to the pool that does the shipment of metrics
    async-threads = 5
    
    # whether to add Kamon environment tags to each of the metrics
    include-environment-tags = false
  }
}
```

- module should start when Kamon is started, you should see "Starting the Kamon CloudWatch extension" in your console output.

# AWS Cloudwatch Example
- log on to Cloudwatch, the metrics will be appearing on 'Custom namespaces' section under "Metrics" menu, i.e.:
![alt text](https://github.com/alonsodomin/kamon-cloudwatch/blob/master/doc/cloudwatch-metrics.jpg "what has showed up in Cloudwatch")

# License
- [Apache V2](https://github.com/alonsodomin/kamon-cloudwatch/blob/master/LICENSE "MIT")
