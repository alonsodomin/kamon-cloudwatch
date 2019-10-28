# Kamon AWS Cloudwatch Extension

[![Build Status](https://travis-ci.org/alonsodomin/kamon-cloudwatch.svg?branch=master)](https://travis-ci.org/alonsodomin/kamon-cloudwatch)
[![GitHub release](https://img.shields.io/github/tag/alonsodomin/kamon-cloudwatch.svg)](https://github.com/alonsodomin/kamon-cloudwatch/releases)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.alonsodomin/kamon-cloudwatch_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.alonsodomin/kamon-cloudwatch_2.12)
[![Mergify Status](https://img.shields.io/endpoint.svg?url=https://gh.mergify.io/badges/alonsodomin/kamon-cloudwatch&style=flat)](https://mergify.io)

## Overview

A simple [Kamon](https://github.com/kamon-io/Kamon) extension to ship metrics data to Cloudwatch using Amazon's async client.

_**Note:** This project has been initially forked from [Timeout's kamon-cloudwatch](https://github.com/timeoutdigital/kamon-cloudwatch) but evolved separately as the original one has fallen out of maintenance._

## Version Compatibility Matrix

The following table maps Kamon core version with the version of this library:

| Kamon Core | Kamon CloudWatch | Scala          | JDK  |
|-----------:| ----------------:| --------------:|-----:|
|      1.0.0 |            1.0.0 | 2.10,2.11,2.12 | 1.8+ |
|      2.0.0 |            1.1.x | 2.11,2.12,2.13 | 1.8+ |

## Getting Started

Add library dependency to your `build.sbt`

```scala
libraryDependencies += "com.github.alonsodomin" %% "kamon-cloudwatch" % "<version>"
```

The module will be loaded automatically and you should see "_Starting the Kamon CloudWatch extension_" message in your logs output.

> **Note:** Be sure the box in which this is going to be used, has the proper access credentials to send data to AWS CloudWatch. The preferred approach would be to either use an _InstanceProfile_ or roles in the case of ECS/Docker Containers. Another way would be to have the environment variables `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` set correctly.

### Kamon 1.0 users

If using Kamon 1.x, you will need to register it manually during application startup:

```scala
val reporter = new CloudWatchReporter()
Kamon.addReporter(reporter)
```

## Configuration

You can configure the module behaviour by overriding any of the following settings in your `application.conf` file:

```
kamon {
  cloudwatch {

    # namespace is the AWS Metrics custom namespace
    namespace = <application name>
    
    # (Optional) AWS region, on ec2 region is fetched by getCurrentRegion command
    region = eu-west-1

    # batch size of data when send to Cloudwatch. Default: 20
    batch-size = 20

    # how many threads will be assigned to the pool that does the shipment of metrics. Default: 5
    async-threads = 5
    
    # whether to add Kamon environment tags to each of the metrics. Default: false
    include-environment-tags = false
  }
}
```

# AWS Cloudwatch Example
- log on to Cloudwatch, the metrics will be appearing on 'Custom namespaces' section under "Metrics" menu, i.e.:
![alt text](https://github.com/alonsodomin/kamon-cloudwatch/blob/master/doc/cloudwatch-metrics.jpg "what has showed up in Cloudwatch")

# License
- [Apache V2](https://github.com/alonsodomin/kamon-cloudwatch/blob/master/LICENSE "MIT")
