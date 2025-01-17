# Ergo Blockchain Explorer

This repository contains 4 services:

### 1. Chain Grabber

`modules/chain-grabber`

Scans blockchain, dumps aggregated data to database

Dependencies:
- Postgres (Schema `modules/explorer-core/src/main/resources/db/V1__Node_data.sql`)

### 2. Explorer API

`modules/explorer-api`

Provides a set of HTTP API methods for querying chain/off-chain data.

Specification: https://api.ergoplatform.com/docs/openapi

Dependencies:
- Postgres (Schema `modules/explorer-core/src/main/resources/db/V1__Node_data.sql`)
- Redis

### 3. UTX Tracker

`modules/utx-tracker`

Dumps unconfirmed transactions from mempool to local database

Dependencies:
- Postgres (Schema `modules/explorer-core/src/main/resources/db/V1__Node_data.sql`)

### 4. UTX Broadcaster

`modules/utx-broadcaster`

Broadcasts unconfirmed transactions to a set of known peers

Dependencies:
- Redis

## Assembly

Service JAR can be build with `sbt <service-name>/assembly`, assembly will appear in `modules/<service-name>/target` directory.

## Configuration

Config samples can be found in `modules/<service-name>/src/main/resources/application.conf`

## Run

SBT project:
`sbt <service-name>/run`

Assembly JAR:
`java -jar <<service-name>-assembly-*.jar>`

Tests:
`sbt test`
