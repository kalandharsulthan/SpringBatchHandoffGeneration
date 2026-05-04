# Architecture

## Overview

The service is a single Spring Batch job exposed via a REST API. Each call to `POST /api/handoff/generate` launches one job execution asynchronously. The job reads rows from PostgreSQL using paginated JDBC cursors, formats each row into a fixed-width text record, and streams the output to a file.

---

## Request Flow

```
HTTP Client
    │
    ▼
HandoffController              POST /api/handoff/generate
    │                          → returns { jobExecutionId, status: "STARTING" } immediately
    ▼
HandoffJobService              builds JobParameters (outputFilePath, run.id, additionalParams)
    │                          calls asyncJobLauncher.run(handoffJob, params)
    ▼
TaskExecutorJobLauncher        non-blocking; spawns job on a new thread (SimpleAsyncTaskExecutor)
    │
    ▼
Spring Batch Job: handoffJob
    │
    └── Step: handoffStep      (chunk-oriented, chunk-size=1000, fault-tolerant, skip-limit=10)
            │
            ├── HandoffItemReader    reads page-by-page from PostgreSQL
            │       └── JdbcPagingItemReader<Map<String,Object>>
            │               SqlPagingQueryProviderFactoryBean
            │               → generates: SELECT ... FROM ... WHERE ... ORDER BY ... LIMIT/OFFSET
            │
            ├── HandoffItemProcessor  converts Map<String,Object> → HandoffRecord
            │       └── FixedWidthFormatter  applies format pattern, truncates, pads each field
            │
            └── FlatFileItemWriter<HandoffRecord>     (@StepScope)
                    └── LineAggregator  String.join("", record.getFields().values())
                    └── FileSystemResource(jobParameters['outputFilePath'])
```

After launch, the client polls:
```
HTTP Client  →  GET /api/handoff/status/{jobExecutionId}
                HandoffController → HandoffJobService → JobExplorer
                ← { status, exitCode, outputFilePath, startTime, endTime, failureMessages }
```

---

## Component Map

| Class | Package | Role |
|---|---|---|
| `HandoffGenerationApplication` | root | Spring Boot entry point, `@EnableConfigurationProperties` |
| `HandoffProperties` | config | `@ConfigurationProperties(prefix="handoff")` — single source of truth for all config |
| `BatchJobConfig` | config | Declares `Job`, `Step`, `@StepScope` reader/writer beans, async `JobLauncher` |
| `HandoffController` | controller | REST endpoints + local `@ExceptionHandler` for `HandoffException` → HTTP 400 |
| `HandoffJobService` | service | Builds `JobParameters`, calls `asyncJobLauncher.run()`, queries `JobExplorer` for status |
| `HandoffJobRequest` | service/dto | Request body: `outputFileName` (optional), `additionalParams` (optional map) |
| `HandoffJobResponse` | service/dto | Response: `jobExecutionId`, `status` |
| `HandoffStatusResponse` | service/dto | Poll response: full job execution details |
| `HandoffItemReader` | batch/reader | Plain class (no `@Component`): factory that builds `JdbcPagingItemReader` from config |
| `HandoffItemProcessor` | batch/processor | `@Component` + `ItemProcessor`: maps DB row to `HandoffRecord` using field definitions |
| `HandoffItemWriter` | batch/writer | Plain class (no `@Component`): delegates to `FlatFileItemWriter`, tracks record count |
| `FixedWidthFormatter` | util | `@Component`, pure logic: apply format → truncate → pad to exact field width |
| `FieldDefinition` | domain | Config model: `name`, `length`, `alignment` (LEFT/RIGHT), `padChar`, optional `format` |
| `HandoffRecord` | domain | `LinkedHashMap<String,String>` — insertion order = file column order |
| `HandoffException` | exception | Unchecked exception wrapping all job launch and lookup failures |

---

## Data Flow Through the Batch Step

```
PostgreSQL row (Map<String,Object>):
  { "account_no": "ACC001", "customer_name": "John Smith", "balance": BigDecimal(1234.56) }

HandoffItemProcessor iterates HandoffProperties.getFields() in declaration order:

  Field: account_no
    rawValue   = "ACC001"
    format     = null  →  toString() = "ACC001"
    length     = 20, 6 < 20, no truncation
    alignment  = LEFT  →  pad right with ' '
    result     = "ACC001              "  (20 chars)

  Field: customer_name
    rawValue   = "John Smith"
    format     = null  →  "John Smith"
    length     = 40, 10 < 40, no truncation
    alignment  = LEFT  →  pad right with ' '
    result     = "John Smith                              "  (40 chars)

  Field: balance
    rawValue   = BigDecimal(1234.56)
    format     = "%.2f"  →  String.format("%.2f", 1234.56) = "1234.56"
    length     = 15, 7 < 15, no truncation
    alignment  = RIGHT  →  pad left with '0'
    result     = "000000001234.56"  (15 chars)

HandoffRecord (LinkedHashMap preserves order):
  { "account_no":    "ACC001              ",
    "customer_name": "John Smith                              ",
    "balance":       "000000001234.56" }

LineAggregator → String.join("", values()):
  "ACC001              John Smith                              000000001234.56"
   └── 75 characters total (20 + 40 + 15)

Written as one line to the output file.
```

---

## Pagination Strategy

`SqlPagingQueryProviderFactoryBean` auto-detects PostgreSQL and generates efficient paging SQL:

```sql
-- Page 1
SELECT account_no, customer_name, balance
FROM accounts
WHERE status = 'ACTIVE'
ORDER BY account_no ASC
LIMIT 1000 OFFSET 0

-- Page 2
LIMIT 1000 OFFSET 1000

-- Page N
LIMIT 1000 OFFSET (N-1)*1000
```

| Config | Effect |
|---|---|
| `handoff.batch.page-size` | Rows fetched per JDBC call |
| `handoff.batch.chunk-size` | Rows processed + written per Spring Batch transaction |
| Keep `page-size == chunk-size` | Standard approach — avoids partial-page reads per transaction |

**Performance requirement:** The `sort-key` column (`account_no`) **must have a B-tree index** in PostgreSQL. Without it, every page requires a full table scan — O(n²) for large datasets.

**Restartability:** If the job fails mid-run, Spring Batch stores the last committed chunk offset in `BATCH_STEP_EXECUTION_CONTEXT`. On restart, `JdbcPagingItemReader` resumes from that offset.

---

## Async Job Execution

```
POST /generate  ──→  asyncJobLauncher.run()  ──→  returns STARTING  ──→  202 response
                              │
                              └── new thread (SimpleAsyncTaskExecutor)
                                        │
                                        └── batch step runs to COMPLETED / FAILED

GET /status/{id}  ──→  JobExplorer.getJobExecution(id)  ──→  live status from DB
```

- The POST endpoint returns within milliseconds regardless of dataset size
- Job state is persisted to Spring Batch metadata tables — survives application restarts
- Callers must poll `GET /status/{id}` to detect completion

---

## Configuration-Driven Design

Everything about the output format lives in `application.yml`. No code changes needed to switch tables or add fields:

```yaml
# Example: transaction handoff instead of account handoff
handoff:
  datasource:
    select-clause: "txn_id, account_no, amount, txn_date"
    from-clause: "transactions"
    where-clause: "txn_date = CURRENT_DATE"
    sort-key: txn_id
  fields:
    - name: txn_id
      length: 12
      alignment: RIGHT
      pad-char: "0"
    - name: account_no
      length: 20
      alignment: LEFT
      pad-char: " "
    - name: amount
      length: 15
      alignment: RIGHT
      pad-char: "0"
      format: "%.2f"
    - name: txn_date
      length: 10
      alignment: LEFT
      pad-char: " "
```

Field names in `fields[].name` must exactly match column names returned by `select-clause` (case-sensitive).

---

## Error Handling

| Layer | Mechanism |
|---|---|
| Bad row during batch | Step is `.faultTolerant().skipLimit(10)` — up to 10 rows skipped per run, logged to `BATCH_STEP_EXECUTION` |
| Job launch failure | `HandoffJobService` wraps all checked exceptions in `HandoffException` |
| REST error response | `HandoffController` `@ExceptionHandler` maps `HandoffException` → HTTP 400 `{ "error": "..." }` |
| Unknown job ID | `HandoffJobService.getJobStatus()` throws `HandoffException("Job execution not found: {id}")` |

---

## Testing Architecture

```
Unit tests (no Spring context)
────────────────────────────────────────────────────────────────────────
FixedWidthFormatterTest     14 parameterized cases — null, empty, exact length,
                            LEFT/RIGHT alignment, truncation, zero-pad, format patterns,
                            blank format string
FieldDefinitionTest         5 cases — getters, defaults, format field
HandoffPropertiesTest       6 cases — nested Output/Batch/Datasource config, defaults
HandoffDtoTest              4 cases — DTO getters for all response/request classes

Unit tests (Mockito)
────────────────────────────────────────────────────────────────────────
HandoffItemProcessorTest    4 cases — field order, null values, missing keys
HandoffItemWriterTest       6 cases — file content, rerun truncation, delegate spy
                                      (verifies close() and update() are delegated)
HandoffJobServiceTest       11 cases — launch, custom filename, additionalParams,
                                       null/non-null times, failure messages, unknown ID

Slice tests
────────────────────────────────────────────────────────────────────────
HandoffControllerTest       5 cases — @WebMvcTest + MockMvc
                            POST with/without body, GET status, error responses

Integration tests
────────────────────────────────────────────────────────────────────────
HandoffJobIntegrationTest   4 cases — @SpringBatchTest + @SpringBootTest + H2 (profile=test)
                            Happy path (25 rows), empty result set,
                            multiple runs (unique run.id), exact fixed-width format check
```

**H2 integration test setup:**
- Profile `test` → `application-test.yml` → `jdbc:h2:mem:testdb;MODE=PostgreSQL;NON_KEYWORDS=VALUE`
- `test_accounts` table created in `@BeforeEach` with `BIGINT AUTO_INCREMENT` (not `BIGSERIAL`)
- Synchronous `TaskExecutorJobLauncher` injected in `@BeforeEach` (no polling needed in tests)
- `JobRepositoryTestUtils.removeJobExecutions()` called before each test for isolation
- PIT excludes integration tests via `<excludedTestClasses>`

---

## Spring Batch Metadata Tables

Auto-created on startup when `spring.batch.jdbc.initialize-schema: always`.

| Table | Purpose |
|---|---|
| `BATCH_JOB_INSTANCE` | One row per unique job name + parameters combination |
| `BATCH_JOB_EXECUTION` | One row per job run attempt (maps to `jobExecutionId` in API) |
| `BATCH_JOB_EXECUTION_PARAMS` | `outputFilePath`, `run.id`, `additionalParams` stored here |
| `BATCH_STEP_EXECUTION` | Read/write/skip/commit counts, timing for `handoffStep` |
| `BATCH_STEP_EXECUTION_CONTEXT` | Restart checkpoint: current page offset |
| `BATCH_JOB_EXECUTION_CONTEXT` | Job-level checkpoint data |

**Useful monitoring queries:**

```sql
-- Recent job runs
SELECT job_execution_id, status, exit_code, start_time, end_time
FROM batch_job_execution
ORDER BY start_time DESC
LIMIT 10;

-- Job parameters (output file path)
SELECT job_execution_id, parameter_name, parameter_value
FROM batch_job_execution_params
WHERE parameter_name = 'outputFilePath'
ORDER BY job_execution_id DESC;

-- Step statistics
SELECT job_execution_id, read_count, write_count, skip_count, commit_count
FROM batch_step_execution
ORDER BY start_time DESC
LIMIT 10;
```

In production, set `spring.batch.jdbc.initialize-schema=never` and create the schema manually before first deployment.
