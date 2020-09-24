# Dremio BigQuery Connector

[![Build Status](https://travis-ci.org/panoramichq/dremio-bigquery-connector.svg?branch=master)](https://travis-ci.org/panoramichq/dremio-bigquery-connector)
![Last Commit](https://img.shields.io/github/last-commit/panoramichq/dremio-bigquery-connector)
![Latest Release](https://img.shields.io/github/v/release/panoramichq/dremio-bigquery-connector)
![License](https://img.shields.io/badge/license-Apache%202-blue)
![Platform](https://img.shields.io/badge/platform-linux%20%7C%20macos%20%7C%20windows-blue)

<!--ts-->
   * [Overview](#overview)
      * [Use Cases](#use-cases)
   * [Downloading a Release](#downloading-a-release)
   * [Usage](#usage)
   * [Development](#development)
      * [Building and Installation](#building-and-installation)
      * [Debugging](#debugging)
   * [Contribution](#contribution)
      * [Submitting an issue](#submitting-an-issue)
      * [Pull Requests](#pull-requests)
<!--te-->

Overview
-----------

This is a community based BigQuery Dremio connector made using the ARP framework. Check [Dremio Hub](https://github.com/dremio-hub) for more examples and [ARP Docs](https://github.com/dremio-hub/dremio-sqllite-connector#arp-file-format) for documentation. 

What is Dremio?
-----------

Dremio delivers lightning fast query speed and a self-service semantic layer operating directly against your data lake storage and other sources. No moving data to proprietary data warehouses or creating cubes, aggregation tables and BI extracts. Just flexibility and control for Data Architects, and self-service for Data Consumers.

Use Cases
-----------

* [Join data](https://www.dremio.com/tutorials/combining-data-from-multiple-datasets/) from BigQuery with other sources (On prem/Cloud)
* Interactive SQL performance with [Data Reflections](https://www.dremio.com/tutorials/getting-started-with-data-reflections/)
* Offload BigQuery tables using [CTAS](https://www.dremio.com/tutorials/high-performance-parallel-exports/) to your cheap data lake storage - HDFS, S3, ADLS
* [Curate Datasets](https://www.dremio.com/tutorials/data-curation-with-dremio/) easily through the self-service platform

Usage
-----------

### Creating a new BigQuery Source

### Required Parameters

* Google Cloud Project ID
    * Ex: `my-big-project-name`.
* Service Account Email & JSON Key
    * You will need to generate an IAM service account with access to the BigQuery resources you want to query. You will need the contents of the JSON key for that account, as well as the email address associated with it.

## Development

Building and Installation
-----------

1. Download and install the Google Simba BigQuery JDBC driver from [the Google website](https://cloud.google.com/bigquery/providers/simba-drivers). Install the main JAR file into your local Maven repository with the following (update the path to match the download path):

```
    mvn install:install-file \
        -Dfile=/Users/build/Downloads/SimbaJDBCDriverforGoogleBigQuery42_1.2.4.1007/GoogleBigQueryJDBC42.jar \
        -DgroupId=com.simba.googlebigquery \
        -DartifactId=googlebigquery-jdbc42 \
        -Dversion=1.2.4.1007 \
        -Dpackaging=jar \
        -DgeneratePom=true
```

2. Generate a shaded BigQuery JDBC client JAR file by running `mvn clean install` inside the `bigquery-driver-shade` directory.
3. In root directory with the pom.xml file run `mvn clean install -DskipTests`.
4. Take the resulting .jar file in the target folder and put it in the <DREMIO_HOME>\jars folder in Dremio.
5. Restart Dremio

Debugging
-----------
To debug pushdowns for queries set the following line in `logback.xml`

```
  <logger name="com.dremio.exec.store.jdbc">
    <level value="${dremio.log.level:-trace}"/>
  </logger>
 ```
  
You can then notice lines like below in server.log file after which you can revist the YAML file to add pushdowns:

```diff
- 2019-07-11 18:56:24,001 [22d879a7-ce3d-f2ca-f380-005a88865700/0:foreman-planning] DEBUG c.d.e.store.jdbc.dialect.arp.ArpYaml - Operator / not supported. Aborting pushdown.
```

You can also take a look at the planning tab/visualized plan of the profile to determine if everything is pushed down or not.

Contribution
------------

### Submitting an issue

* Go to the issue submission page: https://github.com/panoramichq/dremio-bigquery-connector/issues/new/choose. Please select an appropriate category and provide as much details as you can.

### Pull Requests

PRs are welcome. When submitting a PR make sure of the following:

* Try to follow Google's Java style coding when modifying/creating Java related content.
* Use a YAML linter to check the syntactic correctness of YAML file
* Make sure the build passes
* Run basic queries at least to ensure things are working properly
