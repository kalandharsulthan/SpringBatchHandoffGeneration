# Runbook — Spring Batch Handoff File Generation Service

Complete guide for setting up, configuring, running, and operating the service.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Database Setup](#2-database-setup)
3. [Build the Application](#3-build-the-application)
4. [Run the Application](#4-run-the-application)
5. [REST API Reference](#5-rest-api-reference)
6. [Configuration Reference](#6-configuration-reference)
7. [Runtime Job Parameters](#7-runtime-job-parameters)
8. [Output File Format](#8-output-file-format)
9. [Run Tests](#9-run-tests)
10. [Troubleshooting](#10-troubleshooting)

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

# Set JAVA_HOME for the session
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home

# Verify
java -version
# openjdk version "17.0.x"
```

> **If your system defaults to a different Java version**, always prefix Maven commands with `JAVA_HOME=...` as shown in this runbook.

### PostgreSQL via Docker (quick start)

```bash
docker run -d \
  --name handoff-postgres \
  -e POSTGRES_DB=banking \
  -e POSTGRES_USER=bankinguser \
  -e POSTGRES_PASSWORD=changeit \
  -p 5432:5432 \
  postgres:16
```

> If you already have a PostgreSQL container running, note its `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` — you will need them in [Step 4](#4-run-the-application).

---

## 2. Database Setup

### 2.1 Create the source table

Connect to your PostgreSQL instance and run:

```sql
CREATE TABLE IF NOT EXISTS accounts (
    account_no      VARCHAR(20)     NOT NULL,
    customer_name   VARCHAR(100)    NOT NULL,
    balance         DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    status          VARCHAR(10)     NOT NULL DEFAULT 'ACTIVE',
    branch_code     VARCHAR(6),
    account_type    VARCHAR(10),
    created_date    DATE            DEFAULT CURRENT_DATE,
    PRIMARY KEY (account_no)
);

-- Index required for JdbcPagingItemReader sort key performance
CREATE INDEX IF NOT EXISTS idx_accounts_account_no ON accounts(account_no);
CREATE INDEX IF NOT EXISTS idx_accounts_status      ON accounts(status);
```

> **Important**: The sort key column (`account_no` by default) **must have a database index**. Without it, every page of the batch read requires a full table scan — O(n²) for large tables.

### 2.2 Load sample data (optional)

```sql
INSERT INTO accounts (account_no, customer_name, balance, status, branch_code, account_type) VALUES
('ACC00000000000001', 'Arjun Sharma',    125000.75, 'ACTIVE',   'BR001', 'SAVINGS'),
('ACC00000000000002', 'Priya Nair',       89500.00, 'ACTIVE',   'BR001', 'CURRENT'),
('ACC00000000000003', 'Ravi Kumar',      250000.50, 'ACTIVE',   'BR002', 'SAVINGS'),
('ACC00000000000004', 'Sunita Mehta',     45000.00, 'ACTIVE',   'BR002', 'SAVINGS'),
('ACC00000000000005', 'Deepak Verma',    310000.25, 'ACTIVE',   'BR003', 'CURRENT');
```

### 2.3 Spring Batch metadata schema

The service auto-creates Spring Batch metadata tables (`BATCH_JOB_INSTANCE`, `BATCH_JOB_EXECUTION`, etc.) in the same database on first startup when:

```yaml
spring.batch.jdbc.initialize-schema: always   # dev/test
```

For **production**, set this to `never` and create the schema once manually:

```bash
# Extract and run the schema from the Spring Batch JAR
jar tf ~/.m2/repository/org/springframework/batch/spring-batch-core/*/spring-batch-core-*.jar \
  | grep schema-postgresql
# Then run: schema-postgresql.sql against your database
```

---

## 3. Build the Application

```bash
cd SpringBatchHandoffGeneration

# Full build: compile + all tests + JaCoCo coverage check (≥80%)
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
mvn clean verify

# Build only (skip tests — for quick iteration)
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
mvn clean package -DskipTests
```

**Successful build output:**

```
[INFO] Tests run: 59, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time: ~15 seconds
```

**JAR location:** `target/handoff-generation-1.0.0-SNAPSHOT.jar`

---

## 4. Run the Application

### 4.1 Environment variables

Set these before running. Values shown match the current Docker setup:

```bash
export DB_USERNAME=ai_user        # PostgreSQL username
export DB_PASSWORD=ai_password    # PostgreSQL password
```

If you used the Docker quick-start from Step 1, use:
```bash
export DB_USERNAME=bankinguser
export DB_PASSWORD=changeit
```

### 4.2 Create the output directory

```bash
mkdir -p /tmp/handoff-output
```

> Change `handoff.output.directory` in `application.yml` to any writable path.

### 4.3 Start with Maven (development)

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
mvn spring-boot:run
```

### 4.4 Start with JAR (production-style)

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
java -jar target/handoff-generation-1.0.0-SNAPSHOT.jar
```

### 4.5 Start with JVM tuning (high-volume)

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
java \
  -Xms512m -Xmx2g \
  -XX:+UseG1GC \
  -jar target/handoff-generation-1.0.0-SNAPSHOT.jar
```

### 4.6 Start with a custom config file

```bash
java -jar target/handoff-generation-1.0.0-SNAPSHOT.jar \
  --spring.config.location=file:/etc/handoff/application.yml
```

### 4.7 Confirm the application started

```
Started HandoffGenerationApplication in 1.5 seconds (process running for 1.8)
```

Service is ready at: `http://localhost:8080`

---

## 5. REST API Reference

### 5.1 Trigger file generation

**`POST /api/handoff/generate`**

Launches the batch job asynchronously. Returns immediately with a `jobExecutionId`.

#### Request body (optional JSON)

```json
{
  "outputFileName": "ACCT_EXTRACT_20260503.dat",
  "additionalParams": {
    "branchCode": "BR001",
    "reportDate": "20260503"
  }
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `outputFileName` | String | No | Custom output filename. If omitted, auto-generated as `HANDOFF_yyyyMMdd_HHmmss.dat` |
| `additionalParams` | Map | No | Extra key-value pairs added to Spring Batch `JobParameters`. Useful for audit/tracking. |

#### Response `202 Accepted`

```json
{
  "jobExecutionId": 1,
  "status": "STARTING"
}
```

#### Examples

```bash
# Trigger with auto-generated filename
curl -X POST http://localhost:8080/api/handoff/generate \
  -H "Content-Type: application/json"

# Trigger with custom filename
curl -X POST http://localhost:8080/api/handoff/generate \
  -H "Content-Type: application/json" \
  -d '{"outputFileName": "ACCT_EXTRACT_20260503.dat"}'

# Trigger with custom filename and extra params
curl -X POST http://localhost:8080/api/handoff/generate \
  -H "Content-Type: application/json" \
  -d '{
    "outputFileName": "BRANCH_BR001_20260503.dat",
    "additionalParams": {
      "branchCode": "BR001",
      "triggeredBy": "scheduler"
    }
  }'
```

---

### 5.2 Check job status

**`GET /api/handoff/status/{jobExecutionId}`**

Poll this endpoint after triggering generation to track progress.

#### Response `200 OK`

```json
{
  "jobExecutionId": 1,
  "status": "COMPLETED",
  "exitCode": "COMPLETED",
  "startTime": "2026-05-03T19:30:27.017479",
  "endTime": "2026-05-03T19:30:27.095867",
  "outputFilePath": "/tmp/handoff-output/HANDOFF_20260503_193027.dat",
  "failureMessages": []
}
```

| Field | Description |
|---|---|
| `jobExecutionId` | Unique ID assigned at launch |
| `status` | Spring Batch `BatchStatus`: `STARTING`, `STARTED`, `COMPLETED`, `FAILED`, `STOPPED`, `ABANDONED` |
| `exitCode` | Step-level exit: `COMPLETED`, `FAILED`, `NOOP`, `UNKNOWN` |
| `startTime` | ISO-8601 datetime when the job began (null if not yet started) |
| `endTime` | ISO-8601 datetime when the job finished (null if still running) |
| `outputFilePath` | Absolute path to the generated file on the server filesystem |
| `failureMessages` | List of exception messages if job failed or rows were skipped |

#### Status lifecycle

```
STARTING → STARTED → COMPLETED
                   → FAILED
                   → STOPPED
```

#### Example

```bash
curl http://localhost:8080/api/handoff/status/1
```

---

### 5.3 Error responses

Both endpoints return `400 Bad Request` with a JSON error body when something goes wrong:

```json
{
  "error": "Job execution not found: 999"
}
```

Common error messages:

| Error | Cause |
|---|---|
| `Job execution not found: {id}` | No job with that ID in the batch metadata |
| `Failed to launch handoff job: ...` | DB connection failure, invalid config, or job already running with same params |

---

## 6. Configuration Reference

All configuration lives in `src/main/resources/application.yml`. No code changes are needed.

### 6.1 Database connection

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/prod_support_ai   # DB host:port/name
    username: ${DB_USERNAME:ai_user}                        # env var, fallback to default
    password: ${DB_PASSWORD:ai_password}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10     # max DB connections (increase for high concurrency)
      minimum-idle: 2
      connection-timeout: 30000 # ms — fail fast if DB is unavailable
```

### 6.2 Spring Batch settings

```yaml
spring:
  batch:
    job:
      enabled: false            # MUST be false — prevents auto-run at startup
    jdbc:
      initialize-schema: always # always (dev/test) | never (production)
```

### 6.3 Server

```yaml
server:
  port: 8080    # change to 9090 etc. if 8080 is in use
```

### 6.4 Output file settings (`handoff.output`)

```yaml
handoff:
  output:
    directory: /tmp/handoff-output   # output directory — must exist and be writable
    file-prefix: HANDOFF_            # prefix for auto-generated names
    file-suffix: .dat                # suffix for auto-generated names
    encoding: UTF-8                  # UTF-8 or ISO-8859-1 for mainframe consumers
```

**Auto-generated filename pattern:** `{file-prefix}{yyyyMMdd_HHmmss}{file-suffix}`
**Example:** `HANDOFF_20260503_143022.dat`

### 6.5 Batch performance tuning (`handoff.batch`)

```yaml
handoff:
  batch:
    chunk-size: 1000    # rows per transaction / write batch
    page-size: 1000     # rows per JDBC SELECT (pagination)
    skip-limit: 10      # max bad rows before job fails
```

| Setting | Recommendation |
|---|---|
| `chunk-size` = `page-size` | Always keep these equal to avoid partial-page reads per transaction |
| Low volume (<100k rows) | `chunk-size: 500`, `page-size: 500` |
| High volume (1M+ rows) | `chunk-size: 5000`, `page-size: 5000` |
| `skip-limit: 0` | Fail immediately on any bad row (strict mode) |
| `skip-limit: -1` | Not supported — use a large number like `99999` for lenient mode |

### 6.6 SQL query settings (`handoff.datasource`)

```yaml
handoff:
  datasource:
    select-clause: "account_no, customer_name, balance"  # columns (no SELECT keyword)
    from-clause: "accounts"                               # table/view (no FROM keyword)
    where-clause: "status = 'ACTIVE'"                    # filter (no WHERE keyword; optional)
    sort-key: account_no                                  # must be in select-clause + indexed
```

**Join example:**
```yaml
from-clause: "accounts a JOIN customers c ON a.customer_id = c.id"
where-clause: "a.status = 'ACTIVE' AND c.branch_code = 'BR001'"
```

> **Why split clauses?** `SqlPagingQueryProviderFactoryBean` generates the correct `LIMIT/OFFSET` paging SQL for your database. It requires separate clauses — a full SQL string is not supported.

### 6.7 Field definitions (`handoff.fields`)

Fields are output **in the order declared**. Declaration order = file column order.

```yaml
handoff:
  fields:
    - name: account_no       # must match a column name in select-clause (case-sensitive)
      length: 20             # exact character width in output file
      alignment: LEFT        # LEFT (pad right) | RIGHT (pad left)
      pad-char: " "          # padding character
      format:                # optional Java format string (see examples below)

    - name: balance
      length: 15
      alignment: RIGHT
      pad-char: "0"
      format: "%.2f"         # formats 1234.5 → "1234.50" before padding
```

**`format` examples:**

| Value | Input | Output |
|---|---|---|
| *(not set)* | `"ABC"` | raw `toString()` |
| `"%.2f"` | `1234.5` (Double) | `"1234.50"` |
| `"%.2f"` | `BigDecimal("9999.9")` | `"9999.90"` |
| `"%08d"` | `42` | `"00000042"` |

### 6.8 Logging

```yaml
logging:
  level:
    com.banking.handoff: DEBUG    # set to INFO in production
    org.springframework.batch: INFO
```

---

## 7. Runtime Job Parameters

These are passed internally by the service when a job is launched. They appear in Spring Batch metadata tables and in the status response.

| Parameter | Type | Set by | Description |
|---|---|---|---|
| `outputFilePath` | String | Service | Absolute path of the output file. Derived from `handoff.output.directory` + filename. |
| `run.id` | Long | Service | Current epoch milliseconds. Ensures each trigger creates a new unique job instance. |
| *(any key)* | String | API caller | Any key in `additionalParams` from the request body is added as a String job parameter. |

### Override output directory at runtime (command-line property)

```bash
java -jar target/handoff-generation-1.0.0-SNAPSHOT.jar \
  --handoff.output.directory=/mnt/nas/handoff/daily
```

### Override any config property at runtime

Spring Boot allows overriding any `application.yml` key via command-line argument:

```bash
java -jar target/handoff-generation-1.0.0-SNAPSHOT.jar \
  --handoff.batch.chunk-size=5000 \
  --handoff.datasource.where-clause="status='ACTIVE' AND branch_code='BR001'" \
  --server.port=9090
```

### Override via environment variables

Any `application.yml` key can be overridden using environment variables with uppercase and underscores:

| yml key | Environment variable |
|---|---|
| `handoff.output.directory` | `HANDOFF_OUTPUT_DIRECTORY` |
| `handoff.batch.chunk-size` | `HANDOFF_BATCH_CHUNK_SIZE` |
| `handoff.datasource.where-clause` | `HANDOFF_DATASOURCE_WHERE_CLAUSE` |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` |
| `server.port` | `SERVER_PORT` |

```bash
export HANDOFF_OUTPUT_DIRECTORY=/mnt/nas/handoff
export HANDOFF_BATCH_CHUNK_SIZE=5000
java -jar target/handoff-generation-1.0.0-SNAPSHOT.jar
```

---

## 8. Output File Format

The output is a **fixed-width text file**. Each row in the database becomes one line in the file. Line length = sum of all field lengths.

### Current configuration produces 75-character lines

```
|<--- account_no: 20 chars --->|<--------- customer_name: 40 chars -------->|<- balance: 15 ->|
ACC00000000000001   Arjun Sharma                            000000125000.75
ACC00000000000002   Priya Nair                              000000089500.00
ACC00000000000003   Ravi Kumar                              000000250000.50
```

### Formatting rules applied per field

1. **`format`** — if set, `String.format(format, value)` is applied first
2. **Truncation** — if value is longer than `length`, right characters are dropped
3. **Padding**:
   - `LEFT` alignment → value sits at left, spaces (or `pad-char`) fill right
   - `RIGHT` alignment → `pad-char` fills left, value sits at right

### Verifying output

```bash
# Check line count
wc -l /tmp/handoff-output/HANDOFF_20260503_193027.dat

# Verify all lines are the same length (should print one number)
awk '{print length($0)}' /tmp/handoff-output/HANDOFF_20260503_193027.dat | sort -u

# Preview first 5 lines
head -5 /tmp/handoff-output/HANDOFF_20260503_193027.dat

# Check file encoding
file /tmp/handoff-output/HANDOFF_20260503_193027.dat
```

---

## 9. Run Tests

### Unit + integration tests + JaCoCo coverage report

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
mvn clean verify
```

- Runs all 59 tests
- Enforces ≥80% instruction coverage (fails build if below)
- JaCoCo HTML report: `target/site/jacoco/index.html`

### Tests only (no coverage check)

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
mvn test
```

### Single test class

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
mvn test -Dtest=FixedWidthFormatterTest

mvn test -Dtest=HandoffJobIntegrationTest
```

### Mutation testing (PIT) — run separately

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
mvn pitest:mutationCoverage
```

- PIT HTML report: `target/pit-reports/index.html`
- Takes ~15 seconds (unit tests only; integration tests excluded)

### Current coverage results

| Metric | Result |
|---|---|
| Tests | 59 passing, 0 failing |
| JaCoCo instruction | 97% |
| JaCoCo branch | 85% |
| JaCoCo class | 100% |
| PIT mutation score | 97% (72/74 killed) |

---

## 10. Troubleshooting

### Application fails to start: `BeanDefinitionOverrideException`

**Symptom:** `Cannot register bean definition … since there is already … bound`

**Cause:** A batch component class has both `@Component` and a `@Bean` method with the same name in `BatchJobConfig`.

**Fix:** Remove `@Component` from `HandoffItemReader` and `HandoffItemWriter` — they are instantiated by `BatchJobConfig` only.

---

### Application fails to start: `Connection refused` or `FATAL: password authentication failed`

**Symptom:** HikariCP connection pool error at startup.

**Fix:**
```bash
# Verify PostgreSQL is running
docker ps | grep postgres

# Check credentials match application.yml
docker exec <container> psql -U ai_user -d prod_support_ai -c "SELECT 1"

# Override at runtime if needed
java -jar target/*.jar \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/mydb \
  --spring.datasource.username=myuser \
  --spring.datasource.password=mypass
```

---

### Job status stays `STARTING` and never completes

**Cause:** The job launcher is async (`TaskExecutorJobLauncher`). Check the application logs for errors.

```bash
# Tail the application log
tail -f /tmp/handoff-app.log | grep -E "(ERROR|WARN|BatchStatus)"
```

---

### Output file is empty (0 lines)

**Cause:** The `WHERE` clause filtered out all rows.

**Check:**
```bash
# Run the query directly against your database
docker exec <container> psql -U ai_user -d prod_support_ai \
  -c "SELECT COUNT(*) FROM accounts WHERE status = 'ACTIVE'"
```

If count is 0, either no rows match or the `where-clause` in `application.yml` is too restrictive. Update it:
```yaml
handoff.datasource.where-clause: ""    # remove filter entirely
```

---

### `IllegalFormatConversionException` in logs

**Symptom:** `f != java.lang.Integer`

**Cause:** A field with `format: "%.2f"` is receiving an `Integer` value from the database. The `%f` specifier requires `Float`, `Double`, or `BigDecimal`.

**Fix:** Cast the column in the SQL select clause:
```yaml
select-clause: "account_no, customer_name, balance::float"
```

---

### JaCoCo build fails: `Rule violated: instructions covered ratio is 0.7, but expected minimum is 0.8`

**Cause:** Test coverage dropped below the 80% threshold.

**Fix:** Add tests, or temporarily lower the threshold:
```bash
mvn verify -Djacoco.minimum.coverage=0.75
```

---

### Sort key column causes slow queries on large tables

**Symptom:** Each page of the batch takes progressively longer.

**Cause:** Missing index on the sort key column.

**Fix:**
```sql
CREATE INDEX idx_accounts_account_no ON accounts(account_no);
ANALYZE accounts;
```
