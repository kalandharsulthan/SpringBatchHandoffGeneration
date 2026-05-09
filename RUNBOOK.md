# Runbook — Spring Batch Handoff File Generation Service

Complete guide for setting up, configuring, running, and operating the service.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Database Setup](#2-database-setup)
3. [Build the Application](#3-build-the-application)
4. [Run the Application](#4-run-the-application)
5. [REST API Reference](#5-rest-api-reference)
6. [Runtime Job Parameters](#6-runtime-job-parameters)
7. [Output File Format](#7-output-file-format)
8. [Run Tests](#8-run-tests)
9. [Troubleshooting](#9-troubleshooting)

---

## 1. Prerequisites

| Requirement | Version | Check |
|---|---|---|
| Java (JDK) | 17 | `java -version` |
| Apache Maven | 3.8+ | `mvn -version` |
| PostgreSQL | 14+ | running locally or via Docker |

### Java 17 on macOS (Homebrew)

```bash
brew install openjdk@17
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
java -version   # should print openjdk version "17.0.x"
```

Always prefix Maven commands with the `JAVA_HOME` export if your system defaults to a different version.

---

## 2. Database Setup

### 2.1 Create source and staging tables

Connect to PostgreSQL and run:

```sql
-- Source tables (pre-existing production data)
CREATE TABLE IF NOT EXISTS instrument_header (
    instrument_id          VARCHAR(20)   NOT NULL PRIMARY KEY,
    collection_number      VARCHAR(20),
    business_date          DATE,
    draw_reference_number  VARCHAR(30),
    instrument_amount      DECIMAL(15,2),
    bank_code              VARCHAR(10),
    instrument_status      VARCHAR(20)   -- 'ACTIVE' | 'CLOSED'
);

CREATE TABLE IF NOT EXISTS accounting_entry (
    instrument_number  VARCHAR(20)   NOT NULL PRIMARY KEY,
    collection_number  VARCHAR(20),
    accounting_status  VARCHAR(20),  -- 'PAID' | 'WRITTEN_OFF' | 'TECHNICAL_RETURN'
    debit_amount       DECIMAL(15,2),
    credit_amount      DECIMAL(15,2),
    bank_code          VARCHAR(10),
    value_date         DATE,
    entry_status       VARCHAR(20)   -- 'PENDING' | 'PROCESSED'
);

-- Staging tables (populated by populationJob each run)
CREATE TABLE IF NOT EXISTS instrument_header_staging (
    id                     BIGSERIAL     PRIMARY KEY,
    batch_run_id           VARCHAR(36)   NOT NULL,
    instrument_id          VARCHAR(20),
    collection_number      VARCHAR(20),
    business_date          DATE,
    draw_reference_number  VARCHAR(30),
    instrument_amount      DECIMAL(15,2),
    bank_code              VARCHAR(10)
);

CREATE TABLE IF NOT EXISTS accounting_staging (
    id                 BIGSERIAL     PRIMARY KEY,
    batch_run_id       VARCHAR(36)   NOT NULL,
    instrument_number  VARCHAR(20),
    collection_number  VARCHAR(20),
    accounting_status  VARCHAR(20),
    debit_amount       DECIMAL(15,2),
    credit_amount      DECIMAL(15,2),
    bank_code          VARCHAR(10),
    value_date         DATE
);

-- Index required for JdbcPagingItemReader sort key performance
CREATE INDEX IF NOT EXISTS idx_ihs_instrument_id   ON instrument_header_staging(instrument_id);
CREATE INDEX IF NOT EXISTS idx_ihs_batch_run_id    ON instrument_header_staging(batch_run_id);
CREATE INDEX IF NOT EXISTS idx_as_instrument_number ON accounting_staging(instrument_number);
CREATE INDEX IF NOT EXISTS idx_as_batch_run_id      ON accounting_staging(batch_run_id);
```

### 2.2 Create and seed the feed query config table

```sql
CREATE TABLE IF NOT EXISTS feed_query_config (
    feed_name      VARCHAR(50)   NOT NULL PRIMARY KEY,
    select_clause  TEXT          NOT NULL,
    from_clause    TEXT          NOT NULL,
    where_clause   TEXT,
    sort_key       VARCHAR(50)   NOT NULL,
    created_date   TIMESTAMP     DEFAULT now()
);

INSERT INTO feed_query_config (feed_name, select_clause, from_clause, where_clause, sort_key)
VALUES
(
    'INSTRUMENT_FEED',
    'instrument_id, collection_number, business_date, draw_reference_number, instrument_amount, bank_code',
    'instrument_header_staging',
    'batch_run_id = :batchRunId',
    'instrument_id'
),
(
    'ACCOUNTING_FEED',
    'instrument_number, collection_number, accounting_status, debit_amount, credit_amount, bank_code, value_date',
    'accounting_staging',
    'batch_run_id = :batchRunId',
    'instrument_number'
);
```

These rows are read by the feed jobs at step startup. Update them to change query behavior without redeploying.

### 2.3 Spring Batch metadata schema

Auto-created on startup when `spring.batch.jdbc.initialize-schema: always` (default for dev). For production, set to `never` and apply manually:

```bash
# Extract and run the PostgreSQL schema from the Spring Batch JAR
jar tf ~/.m2/repository/org/springframework/batch/spring-batch-core/*/spring-batch-core-*.jar \
  | grep schema-postgresql
# Then run schema-postgresql.sql against your database
```

### 2.4 Load sample data (optional)

```sql
INSERT INTO instrument_header VALUES
('INSTR-001', 'COL-001', '2026-05-09', 'DRAW-001', 15000.00, 'BANKX', 'ACTIVE'),
('INSTR-002', 'COL-002', '2026-05-09', 'DRAW-002', 25000.50, 'BANKY', 'ACTIVE');

INSERT INTO accounting_entry VALUES
('INSTR-001', 'COL-001', 'PAID',        15000.00, 0.00,     'BANKX', '2026-05-09', 'PENDING'),
('INSTR-002', 'COL-002', 'WRITTEN_OFF', 0.00,     25000.50, 'BANKY', '2026-05-09', 'PENDING');
```

---

## 3. Build the Application

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home

# Full build: compile + all tests + JaCoCo coverage check (≥80%)
mvn clean verify

# Build only (skip tests)
mvn clean package -DskipTests
```

**JAR location:** `target/handoff-generation-1.0.0-SNAPSHOT.jar`

---

## 4. Run the Application

### 4.1 Environment variables

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export DB_USERNAME=ai_user
export DB_PASSWORD=ai_password
```

**Current Docker setup:** container `pgvector-db`, DB `prod_support_ai`, port `5432`.

### 4.2 Create the output directory

```bash
mkdir -p /tmp/handoff-output
```

### 4.3 Start with Maven (development)

```bash
mvn spring-boot:run
```

### 4.4 Start with JAR (production-style)

```bash
java -jar target/handoff-generation-1.0.0-SNAPSHOT.jar
```

### 4.5 Override config at runtime

```bash
java -jar target/handoff-generation-1.0.0-SNAPSHOT.jar \
  --handoff.output.directory=/mnt/nas/handoff \
  --handoff.batch.chunk-size=5000 \
  --server.port=9090
```

Service is ready at `http://localhost:8080` once you see:
```
Started HandoffGenerationApplication in 1.5 seconds
```

---

## 5. REST API Reference

### 5.1 Trigger the full pipeline

**`POST /api/handoff/pipeline`**

Launches `populationJob → instrumentFeedJob → accountingFeedJob` asynchronously. Returns immediately.

```bash
curl -X POST http://localhost:8080/api/handoff/pipeline
```

**Response `202 Accepted`:**
```json
{
  "runId": "a3f9c2d1-...",
  "batchRunId": "8e1b7d4c-...",
  "status": "ACCEPTED"
}
```

### 5.2 Poll aggregated pipeline status

**`GET /api/handoff/pipeline/{runId}`**

```bash
curl http://localhost:8080/api/handoff/pipeline/a3f9c2d1-...
```

**Response `200 OK`:**
```json
{
  "runId": "a3f9c2d1-...",
  "batchRunId": "8e1b7d4c-...",
  "status": "COMPLETED",
  "errorMessage": null,
  "stagingJobExecutionId": 1,
  "stagingStatus": "COMPLETED",
  "instrumentJobExecutionId": 2,
  "instrumentStatus": "COMPLETED",
  "instrumentOutputFilePath": "/tmp/handoff-output/INSTRUMENT_FEED_20260509_143022_8e1b7d4c.dat",
  "accountingJobExecutionId": 3,
  "accountingStatus": "COMPLETED",
  "accountingOutputFilePath": "/tmp/handoff-output/ACCOUNTING_FEED_20260509_143025_8e1b7d4c.csv"
}
```

**Pipeline `status` values:**

| Status | Meaning |
|---|---|
| `ACCEPTED` | Pipeline accepted, not yet started |
| `RUNNING` | Jobs in progress |
| `COMPLETED` | All three jobs completed successfully |
| `FAILED_STAGING` | `populationJob` failed; feed jobs did not run |
| `FAILED_INSTRUMENT_FEED` | `instrumentFeedJob` failed; accounting feed did not run |
| `FAILED_ACCOUNTING_FEED` | `accountingFeedJob` failed |
| `FAILED` | Unexpected exception in orchestration |

### 5.3 Check a single job execution

**`GET /api/handoff/status/{jobExecutionId}`**

Use the `stagingJobExecutionId`, `instrumentJobExecutionId`, or `accountingJobExecutionId` from the pipeline status response.

```bash
curl http://localhost:8080/api/handoff/status/2
```

**Response `200 OK`:**
```json
{
  "jobExecutionId": 2,
  "status": "COMPLETED",
  "exitCode": "COMPLETED",
  "startTime": "2026-05-09T14:30:22.017",
  "endTime": "2026-05-09T14:30:22.095",
  "outputFilePath": "/tmp/handoff-output/INSTRUMENT_FEED_20260509_143022_8e1b7d4c.dat",
  "failureMessages": []
}
```

### 5.4 Error responses

Both endpoints return `400 Bad Request` on failure:

```json
{ "error": "Pipeline execution not found: unknown-id" }
```

---

## 6. Runtime Job Parameters

These are set internally by `PipelineJobService` when launching jobs.

| Parameter | Jobs | Description |
|---|---|---|
| `batchRunId` | all | UUID shared across all three jobs; used to correlate staging rows to feed output |
| `outputFilePath` | instrument, accounting | Absolute path of the output file |
| `run.id` | all | Current `System.currentTimeMillis()` — ensures unique job instances per trigger |

---

## 7. Output File Format

### Instrument feed (FIXED_WIDTH)

Each staging row becomes one fixed-width line. Current field widths: 20+20+10+30+15+10 = **105 characters per line**.

```
|<-- instrument_id: 20 -->|<-- collection_number: 20 -->|<biz_date: 10>|<-- draw_reference_number: 30 -->|<-- amount: 15 -->|<code: 10>|
INSTR-001            COL-001              2026-05-09DRAW-001                       000000015000.00BANKX
```

### Accounting feed (CSV)

Each staging row becomes one comma-separated line with 7 fields (padded to their configured lengths):

```
INSTR-001            ,COL-001              ,PAID                ,000000015000.00,000000000000.00,BANKX     ,2026-05-09
```

### Verify output

```bash
# Check line count
wc -l /tmp/handoff-output/INSTRUMENT_FEED_*.dat

# Verify all fixed-width lines are 105 chars
awk '{print length($0)}' /tmp/handoff-output/INSTRUMENT_FEED_*.dat | sort -u
# should print only: 105

# Preview first 5 lines
head -5 /tmp/handoff-output/INSTRUMENT_FEED_*.dat
head -5 /tmp/handoff-output/ACCOUNTING_FEED_*.csv
```

---

## 8. Run Tests

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home

# All tests + JaCoCo coverage (≥80% instruction threshold)
mvn clean verify

# Tests only (no coverage enforcement)
mvn test

# Single test class
mvn test -Dtest=HandoffJobIntegrationTest
mvn test -Dtest=FixedWidthFormatterTest

# Skip coverage threshold for quick iteration
mvn clean install -Djacoco.minimum.coverage=0

# Mutation testing — SEPARATE command (instrumentation conflict with JaCoCo)
mvn pitest:mutationCoverage
```

**Coverage results (current):**

| Metric | Result |
|---|---|
| Tests | 61 passing, 0 failing |
| JaCoCo instruction | 97% (threshold: 80%) |
| JaCoCo branch | 85% |
| JaCoCo class | 100% |

JaCoCo HTML report: `target/site/jacoco/index.html`  
PIT HTML report: `target/pit-reports/index.html`

---

## 9. Troubleshooting

### Application fails to start: `BeanDefinitionOverrideException`

**Cause:** `@Component` added to `FeedItemReader` or `StagingItemWriter`. These classes are instantiated exclusively by `@StepScope` `@Bean` methods in the job config classes. Remove `@Component`.

---

### Application fails to start: connection refused or authentication failed

```bash
docker ps | grep pgvector
docker exec pgvector-db psql -U ai_user -d prod_support_ai -c "SELECT 1"

# Override connection at runtime
java -jar target/*.jar \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/mydb \
  --spring.datasource.username=myuser \
  --spring.datasource.password=mypass
```

---

### Pipeline status stays `RUNNING` indefinitely

Check application logs for `ERROR` lines from Spring Batch. The `@Async` thread may have silently failed.

```bash
docker logs <app-container> | grep -E "ERROR|FAILED"
```

---

### Instrument or accounting feed file is empty (0 lines)

The `feed_query_config` WHERE clause filtered out all staging rows for this `batchRunId`. Verify:

```sql
SELECT COUNT(*) FROM instrument_header_staging WHERE batch_run_id = '<batchRunId>';
SELECT * FROM feed_query_config WHERE feed_name = 'INSTRUMENT_FEED';
```

If count is 0, check that `populationJob` ran successfully and that source table rows match the `where-clause` in `instrument-staging.source`.

---

### `EmptyResultDataAccessException` at step startup

**Cause:** `feed_query_config` table missing a row for `INSTRUMENT_FEED` or `ACCOUNTING_FEED`. `FeedQueryConfigRepository.findByFeedName()` throws when no row is found.

```sql
SELECT feed_name FROM feed_query_config;
-- Should return: INSTRUMENT_FEED, ACCOUNTING_FEED
```

Insert the missing row (see Database Setup section 2.2).

---

### `IllegalFormatConversionException` in logs

**Symptom:** `f != java.lang.Integer`

**Cause:** A field with `format: "%.2f"` is receiving an `Integer` from the database. Cast in the `feed_query_config` select clause:

```sql
UPDATE feed_query_config
SET select_clause = 'instrument_id, CAST(instrument_amount AS FLOAT) AS instrument_amount, ...'
WHERE feed_name = 'INSTRUMENT_FEED';
```

---

### JaCoCo build fails: coverage below threshold

```bash
# Temporarily lower the threshold
mvn verify -Djacoco.minimum.coverage=0.75
```

---

### Sort key causes slow queries on large tables

```sql
CREATE INDEX idx_ihs_instrument_id ON instrument_header_staging(instrument_id);
ANALYZE instrument_header_staging;
```

The sort key column **must have a B-tree index** — without it, every page requires a full table scan (O(n²) for large datasets).
