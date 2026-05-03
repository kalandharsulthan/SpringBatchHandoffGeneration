# Architecture

## Overview

The service is a single Spring Batch job exposed via a REST API. Each call to `POST /api/handoff/generate` launches one job execution asynchronously. The job reads rows from PostgreSQL using paginated JDBC cursors, formats each row into a fixed-width text record, and streams the output to a file.

---

## Request Flow

```
HTTP Client
    │
    ▼
HandoffController          POST /api/handoff/generate
    │                      → returns { jobExecutionId, status: "STARTING" } immediately
    ▼
HandoffJobService          builds JobParameters (outputFilePath, run.id)
    │                      calls asyncJobLauncher.run(handoffJob, params)
    ▼
TaskExecutorJobLauncher    non-blocking; spawns job on a new thread
    │
    ▼
Spring Batch Job: handoffJob
    │
    └── Step: handoffStep  (chunk-oriented, chunk-size=1000)
            │
            ├── HandoffItemReader    reads page-by-page from PostgreSQL
            │       └── JdbcPagingItemReader<Map<String,Object>>
            │               SqlPagingQueryProviderFactoryBean → generates LIMIT/OFFSET SQL
            │
            ├── HandoffItemProcessor converts Map<String,Object> → HandoffRecord
            │       └── FixedWidthFormatter  pads/truncates each field value
            │
            └── FlatFileItemWriter<HandoffRecord>
                    └── LineAggregator  joins field values → single fixed-width line
                    └── FileSystemResource(outputFilePath)
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
| `HandoffGenerationApplication` | root | Spring Boot entry point |
| `HandoffProperties` | config | `@ConfigurationProperties(prefix="handoff")` — single source of truth for all config |
| `BatchJobConfig` | config | Declares `Job`, `Step`, `@StepScope` reader/writer, async `JobLauncher` |
| `HandoffController` | controller | REST endpoints + `HandoffException` → HTTP 400 handler |
| `HandoffJobService` | service | Orchestrates launch, builds `JobParameters`, queries `JobExplorer` for status |
| `HandoffItemReader` | batch/reader | Factory: creates `JdbcPagingItemReader` from config |
| `HandoffItemProcessor` | batch/processor | Maps each DB row (`Map<String,Object>`) to `HandoffRecord` using field definitions |
| `HandoffItemWriter` | batch/writer | Delegating wrapper around `FlatFileItemWriter`; tracks record count |
| `FixedWidthFormatter` | util | Pure Java: applies optional format pattern, truncates, pads to exact field width |
| `FieldDefinition` | domain | Config model: name, length, alignment (LEFT/RIGHT), padChar, optional format |
| `HandoffRecord` | domain | `LinkedHashMap<String,String>` — field insertion order = file column order |
| `HandoffException` | exception | Unchecked exception wrapping all job/IO failures |

---

## Data Flow Through the Batch Step

```
PostgreSQL row (Map<String,Object>):
  { "account_no": "ACC001", "customer_name": "John Smith", "balance": BigDecimal(1234.56) }

HandoffItemProcessor + FixedWidthFormatter:
  account_no  → format=null  → "ACC001"           → LEFT  pad ' ' to 20 → "ACC001              "
  customer_name → format=null → "John Smith"       → LEFT  pad ' ' to 40 → "John Smith                              "
  balance     → format="%.2f"→ "1234.56"           → RIGHT pad '0' to 15 → "000000001234.56"

HandoffRecord.fields (LinkedHashMap, insertion order preserved):
  { "account_no": "ACC001              ",
    "customer_name": "John Smith                              ",
    "balance": "000000001234.56" }

LineAggregator (String.join of values):
  "ACC001              John Smith                              000000001234.56"
  └── 75 characters total (20 + 40 + 15)

Written to file as one line per record.
```

---

## Pagination Strategy

`SqlPagingQueryProviderFactoryBean` auto-detects the database (PostgreSQL) and generates:
```sql
SELECT account_no, customer_name, balance
FROM accounts
WHERE status = 'ACTIVE'
ORDER BY account_no ASC
LIMIT 1000 OFFSET 0    -- page 1
LIMIT 1000 OFFSET 1000 -- page 2
...
```

- `page-size` controls rows fetched per JDBC call.
- `chunk-size` controls how many rows are processed + written per transaction.
- Setting both equal (default: 1000) is the standard approach for throughput.
- The sort key column **must be indexed** in PostgreSQL. Without an index, every page requires a full table scan — O(n²) for large tables.

**Restartability**: if the job fails mid-run, Spring Batch records the last committed chunk in the job repository. On restart, the `JdbcPagingItemReader` resumes from the last successful page based on the saved `ExecutionContext`.

---

## Async Job Execution

The `TaskExecutorJobLauncher` uses `SimpleAsyncTaskExecutor`, so:
- `POST /api/handoff/generate` returns with `status: "STARTING"` within milliseconds
- The actual batch work runs on a separate thread
- Callers must poll `GET /api/handoff/status/{id}` to detect completion
- Job metadata (status, times, parameters) is persisted to the Spring Batch tables in PostgreSQL

---

## Configuration-Driven Design

Everything about the output format lives in `application.yml`. To reconfigure for a different banking handoff (e.g., a different table or different fields):

```yaml
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

No code changes are required. The field names in `fields[].name` must exactly match column names returned by `select-clause`.

---

## Error Handling

- `HandoffException` (unchecked) wraps all job launch and status lookup failures
- `HandoffController` has a local `@ExceptionHandler` that maps `HandoffException` → HTTP 400 with `{ "error": "<message>" }`
- The batch step is configured with `.faultTolerant().skipLimit(10).skip(Exception.class)` — up to 10 bad rows per step execution are skipped and logged rather than aborting the job. `skip-limit` is configurable in `application.yml`.
- Skipped items are recorded in Spring Batch's `BATCH_STEP_EXECUTION` metadata

---

## Testing Architecture

```
FixedWidthFormatterTest       ← pure unit, no Spring, parameterized (10 cases)
HandoffItemProcessorTest      ← Mockito mocks HandoffProperties + FixedWidthFormatter
HandoffItemWriterTest         ← real FlatFileItemWriter + @TempDir filesystem
HandoffJobServiceTest         ← Mockito mocks JobLauncher, Job, JobExplorer
HandoffControllerTest         ← @WebMvcTest slice, MockMvc, @MockBean service
HandoffJobIntegrationTest     ← @SpringBatchTest + @SpringBootTest + H2 (profile=test)
                                 runs the full job end-to-end in-memory
```

Integration test H2 setup:
- Profile `test` → `application-test.yml` → `jdbc:h2:mem:testdb;MODE=PostgreSQL`
- `test_accounts` table created in `@BeforeEach` with `BIGINT AUTO_INCREMENT` (not `BIGSERIAL`)
- Spring Batch schema auto-initialized against H2
- `JobRepositoryTestUtils.removeJobExecutions()` called before each test for isolation

---

## Spring Batch Metadata Tables

Spring Batch persists job/step execution metadata to these tables (auto-created):

| Table | Purpose |
|---|---|
| `BATCH_JOB_INSTANCE` | One row per unique job name + parameters combination |
| `BATCH_JOB_EXECUTION` | One row per job run attempt |
| `BATCH_JOB_EXECUTION_PARAMS` | Job parameters for each execution |
| `BATCH_STEP_EXECUTION` | Read/write/skip counts, timing per step |
| `BATCH_STEP_EXECUTION_CONTEXT` | Restart checkpoint data |

In production, set `spring.batch.jdbc.initialize-schema=never` and run the schema SQL manually before first deploy.
