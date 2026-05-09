# Architecture

## Overview

The service is a three-job Spring Batch pipeline exposed via REST API. Each call to `POST /api/handoff/pipeline` launches the pipeline asynchronously. `PipelineJobService` runs all three jobs sequentially on a background thread using a synchronous `JobLauncher`, then reports the aggregated status via in-memory state. Callers poll `GET /api/handoff/pipeline/{runId}`.

---

## Pipeline Flow

```
HTTP Client
    │
    ▼
HandoffController              POST /api/handoff/pipeline
    │                          → returns { runId, batchRunId, status: "ACCEPTED" } immediately
    ▼
PipelineJobService
    │  startPipeline()  → stores PipelineExecution in ConcurrentHashMap, calls runPipelineAsync()
    │
    └── @Async thread (SimpleAsyncTaskExecutor)
            │
            ▼
            PipelineJobService.runPipelineAsync()
            │   uses syncJobLauncher (blocking, sequential)
            │
            ├─ 1. populationJob ──────────────────────────────────────────────────┐
            │       │                                                              │
            │       ├── instrumentPopulationStep                                  │
            │       │     reader: JdbcPagingItemReader(instrument_header)         │
            │       │     writer: JdbcBatchItemWriter(instrument_header_staging)  │
            │       │             + batch_run_id injected via @StepScope          │
            │       │                                                              │
            │       └── accountingPopulationStep                                  │
            │             reader: JdbcPagingItemReader(accounting_entry)          │
            │             writer: JdbcBatchItemWriter(accounting_staging)         │
            │                     + batch_run_id injected                         │
            │                                                               if FAILED
            │   if COMPLETED                                               → stop, mark FAILED_STAGING
            │
            ├─ 2. instrumentFeedJob ───────────────────────────────────────────────┐
            │       │                                                               │
            │       └── instrumentFeedStep                                          │
            │             reader: JdbcPagingItemReader                             │
            │                     query from feed_query_config('INSTRUMENT_FEED')  │
            │             processor: FeedItemProcessor → HandoffRecord             │
            │             writer: FeedItemWriterFactory(FIXED_WIDTH) → .dat file   │
            │                                                                if FAILED
            │   if COMPLETED                                                → stop
            │
            └─ 3. accountingFeedJob
                    │
                    └── accountingFeedStep
                          reader: JdbcPagingItemReader
                                  query from feed_query_config('ACCOUNTING_FEED')
                          processor: FeedItemProcessor → HandoffRecord
                          writer: FeedItemWriterFactory(CSV) → .csv file

GET /api/handoff/pipeline/{runId}  →  PipelineJobService.getPipelineStatus()
                                   →  reads from ConcurrentHashMap<String, PipelineExecution>

GET /api/handoff/status/{jobExecutionId}  →  JobExplorer.getJobExecution(id)
                                          →  live status from Spring Batch metadata tables
```

---

## Component Map

| Class | Package | Role |
|---|---|---|
| `HandoffGenerationApplication` | root | Spring Boot entry point, `@EnableConfigurationProperties` |
| `FeedProperties` | config | `@ConfigurationProperties(prefix="handoff")` — all YAML config; has `instrumentStaging`, `accountingStaging`, `instrument`, `accounting` sub-configs |
| `PopulationJobConfig` | config | Declares `populationJob` with two chained steps; wires `FeedItemReader` + `StagingItemWriter` factories per step |
| `InstrumentFeedJobConfig` | config | Declares `instrumentFeedJob`; reader loads query from `FeedQueryConfigRepository`; writer from `FeedItemWriterFactory` |
| `AccountingFeedJobConfig` | config | Same pattern as `InstrumentFeedJobConfig` for `accountingFeedJob` |
| `SharedBatchConfig` | config | Two `JobLauncher` beans: `asyncJobLauncher` (for REST) and `syncJobLauncher` (for sequential pipeline chaining) |
| `HandoffController` | controller | REST endpoints + `@ExceptionHandler(HandoffException)` → HTTP 400 |
| `PipelineJobService` | service | `@Async` orchestrator; stores `PipelineExecution` objects in `ConcurrentHashMap`; uses `syncJobLauncher` to chain jobs |
| `PipelineExecution` | service | In-memory VO: `runId`, `batchRunId`, per-job execution IDs, statuses, and output file paths |
| `FeedQueryConfigRepository` | batch/reader | `@Component`; reads `SELECT/FROM/WHERE/SORT_KEY` from `feed_query_config` table; called inside `@StepScope` reader beans |
| `FeedItemReader` | batch/reader | Plain factory class (no `@Component`); builds `JdbcPagingItemReader<Map<String,Object>>` from `DatasourceConfig` |
| `FeedItemProcessor` | batch/processor | `ItemProcessor<Map<String,Object>, HandoffRecord>`; applies `FixedWidthFormatter` per `FieldDefinition` |
| `StagingItemWriter` | batch/writer | Plain factory class; builds `JdbcBatchItemWriter` with dynamic INSERT statement; normalises Map keys to lowercase |
| `FeedItemWriterFactory` | batch/writer | `@Component`; creates `FlatFileItemWriter<HandoffRecord>` for CSV or FIXED_WIDTH based on `FeedProperties.Format` |
| `HandoffItemWriter` | batch/writer | Delegating wrapper around `FlatFileItemWriter`; tracks record count |
| `FixedWidthFormatter` | util | `@Component`, pure logic: apply format string → truncate → pad to exact field width |
| `FieldDefinition` | domain | Config model: `name`, `length`, `alignment` (LEFT/RIGHT), `padChar`, optional `format` |
| `HandoffRecord` | domain | `LinkedHashMap<String,String>` — insertion order = file column order |
| `HandoffException` | exception | Unchecked exception wrapping all job launch and lookup failures |

---

## Data Flow: Instrument Feed Step

```
feed_query_config row ('INSTRUMENT_FEED'):
  select_clause: "instrument_id, collection_number, business_date, draw_reference_number, instrument_amount, bank_code"
  from_clause:   "instrument_header_staging"
  where_clause:  "batch_run_id = :batchRunId"
  sort_key:      "instrument_id"

PostgreSQL row (Map<String,Object>):
  { "instrument_id": "INSTR-001", "collection_number": "COL-001",
    "business_date": "2026-05-09", "draw_reference_number": "DRAW-001",
    "instrument_amount": BigDecimal(15000.00), "bank_code": "BANKX" }

FeedItemProcessor iterates FeedProperties.getInstrument().getFields() in declaration order:

  Field: instrument_id (length=20, LEFT, pad=' ')
    → "INSTR-001           " (11 spaces)

  Field: collection_number (length=20, LEFT, pad=' ')
    → "COL-001             " (13 spaces)

  Field: business_date (length=10, LEFT, pad=' ')
    → "2026-05-09"

  Field: draw_reference_number (length=30, LEFT, pad=' ')
    → "DRAW-001                      " (22 spaces)

  Field: instrument_amount (length=15, RIGHT, pad='0', format="%.2f")
    → "000000015000.00"

  Field: bank_code (length=10, LEFT, pad=' ')
    → "BANKX     " (5 spaces)

HandoffRecord (LinkedHashMap):
  { "instrument_id": "INSTR-001           ",
    "collection_number": "COL-001             ",
    "business_date": "2026-05-09",
    "draw_reference_number": "DRAW-001                      ",
    "instrument_amount": "000000015000.00",
    "bank_code": "BANKX     " }

FIXED_WIDTH: String.join("", values()) → 105-character line
CSV:         String.join(",", values()) → comma-separated line
```

---

## Pagination Strategy

`SqlPagingQueryProviderFactoryBean` auto-detects PostgreSQL and generates:

```sql
-- Page 1
SELECT instrument_id, collection_number, ...
FROM instrument_header_staging
WHERE batch_run_id = :batchRunId
ORDER BY instrument_id ASC
LIMIT 1000 OFFSET 0

-- Page N
LIMIT 1000 OFFSET (N-1)*1000
```

`chunk-size == page-size` avoids partial-page reads per transaction. The `sort-key` column must have a B-tree index. Spring Batch stores the last committed chunk offset in `BATCH_STEP_EXECUTION_CONTEXT` for restartability.

---

## Async Execution Model

```
POST /pipeline  →  PipelineJobService.startPipeline()
                     stores PipelineExecution in ConcurrentHashMap
                     calls runPipelineAsync(pe)
                     returns runId immediately (202 Accepted)

@Async thread: populationJob → instrumentFeedJob → accountingFeedJob
               (each via syncJobLauncher.run())

GET /pipeline/{runId}  →  reads from ConcurrentHashMap
                           never touches DB for pipeline-level status
GET /status/{jobExecId}  →  JobExplorer.getJobExecution(id) from Spring Batch metadata tables
```

Job state is persisted to Spring Batch metadata tables, so individual step statuses survive restarts. Pipeline-level state (`PipelineExecution`) is in-memory only and is lost on restart.

---

## Configuration-Driven Design

Population queries and field format rules are in `application.yml`. Feed generation queries are in `feed_query_config`. No code changes are needed to:

- Change source table filters (update `instrument-staging.source.where-clause` or `feed_query_config` row)
- Add or reorder output fields (add/reorder under `handoff.instrument.fields`)
- Switch from fixed-width to CSV (change `handoff.instrument.format: CSV`)
- Disable a feed (set `handoff.accounting.enabled: false`)

---

## Error Handling

| Layer | Mechanism |
|---|---|
| Bad row during batch | Step is `.faultTolerant().skipLimit(N)` — up to `skip-limit` rows skipped per step, recorded in `BATCH_STEP_EXECUTION` |
| Job launch failure | `PipelineJobService` wraps exceptions in `HandoffException`; pipeline marks status `FAILED` |
| REST error response | `HandoffController` `@ExceptionHandler` maps `HandoffException` → HTTP 400 `{ "error": "..." }` |
| Unknown run/job ID | Throws `HandoffException("Pipeline/Job execution not found: {id}")` → 400 |

---

## Testing Architecture

```
Unit tests (no Spring context)
────────────────────────────────────────────────────────────────────────
FixedWidthFormatterTest      14 parameterized cases — null, empty, LEFT/RIGHT,
                             truncation, zero-pad, format patterns, blank format
FieldDefinitionTest          5 cases — getters, defaults, format field
FeedPropertiesTest           6 cases — Output/Batch/StagingConfig/FeedConfig, Format enum, defaults
HandoffDtoTest               4 cases — DTO getters for all response/request classes
FeedItemWriterFactoryTest    3 cases — CSV comma-join, FIXED_WIDTH concat, encoding respected

Unit tests (Mockito)
────────────────────────────────────────────────────────────────────────
FeedItemProcessorTest        4 cases — field order, null values, missing keys
HandoffItemWriterTest        6 cases — delegation, record count, close/update forwarding
FeedQueryConfigRepositoryTest 2 cases — query uses feedName as parameter, maps columns correctly
PipelineJobServiceTest       8 cases — happy path, staging failure stops pipeline,
                             disabled feeds skipped, unknown runId/execId throw

Slice tests
────────────────────────────────────────────────────────────────────────
HandoffControllerTest        5 cases — @WebMvcTest + MockMvc
                             POST pipeline, GET pipeline/{runId}, GET status/{id}, errors

Integration tests
────────────────────────────────────────────────────────────────────────
HandoffJobIntegrationTest    4 cases — @SpringBatchTest + @SpringBootTest + H2 (profile=test)
  populationJobShouldPopulateBothStagingTables
  instrumentFeedJobShouldWriteFixedWidthFile   (105 chars/line, correct zero-padding)
  accountingFeedJobShouldWriteCsvFile          (7 comma-separated fields per row)
  populationJobShouldIsolateRunsByBatchRunId
```

**H2 integration test setup:**
- Profile `test` → `application-test.yml` → H2 `MODE=PostgreSQL;NON_KEYWORDS=VALUE`
- All 5 tables created in `@BeforeEach` with `BIGINT GENERATED BY DEFAULT AS IDENTITY`
- `feed_query_config` seeded in `@BeforeEach` pointing to H2 test tables
- Synchronous `TaskExecutorJobLauncher` used in tests (no polling needed)
- `jobRepositoryTestUtils.removeJobExecutions()` called before each test for isolation
- PIT excludes integration tests via `<excludedTestClasses>`

---

## Spring Batch Metadata Tables

Auto-created on startup when `spring.batch.jdbc.initialize-schema: always`.

| Table | Purpose |
|---|---|
| `BATCH_JOB_INSTANCE` | One row per unique job name + parameters combination |
| `BATCH_JOB_EXECUTION` | One row per job run attempt (maps to `jobExecutionId` in API) |
| `BATCH_JOB_EXECUTION_PARAMS` | `batchRunId`, `outputFilePath`, `run.id` stored here |
| `BATCH_STEP_EXECUTION` | Read/write/skip/commit counts per step |
| `BATCH_STEP_EXECUTION_CONTEXT` | Restart checkpoint: current page offset |

```sql
-- Recent pipeline runs (all 3 jobs)
SELECT job_execution_id, job_instance_id, status, exit_code, start_time, end_time
FROM batch_job_execution
ORDER BY start_time DESC LIMIT 15;

-- Output file paths
SELECT job_execution_id, string_val
FROM batch_job_execution_params
WHERE parameter_name = 'outputFilePath'
ORDER BY job_execution_id DESC;

-- Step read/write counts
SELECT job_execution_id, step_name, read_count, write_count, skip_count
FROM batch_step_execution
ORDER BY start_time DESC LIMIT 15;
```
