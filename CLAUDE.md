# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Batch 5 / Spring Boot 3.3.x banking handoff file generation service.
Orchestrates a three-job pipeline triggered via REST API (async — POST returns immediately, client polls status). Generates fixed-width or CSV feed files from PostgreSQL source tables via configurable staging and externalized query config.

**Stack**: Java 17, Spring Boot 3.3.x, Spring Batch 5, PostgreSQL, Maven  
**Package root**: `com.banking.handoff`

---

## Build Commands

```bash
# Set Java 17 (required — system may default to a different version)
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home

# Full build: compile + all tests + JaCoCo coverage check (≥80%)
mvn clean verify

# Tests only (skip coverage enforcement)
mvn test

# Single test class
mvn test -Dtest=FixedWidthFormatterTest
mvn test -Dtest=HandoffJobIntegrationTest

# Skip coverage threshold for quick local iteration
mvn clean install -Djacoco.minimum.coverage=0

# Mutation testing — run SEPARATELY from verify (bytecode instrumentation conflict)
mvn pitest:mutationCoverage
```

---

## Run Locally

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export DB_USERNAME=ai_user
export DB_PASSWORD=ai_password
mvn spring-boot:run
```

**Current Docker PostgreSQL setup:**
- Container: `pgvector-db`
- DB: `prod_support_ai` | User: `ai_user` | Password: `ai_password` | Port: `5432`
- Output directory: `/tmp/handoff-output`

```bash
# Access PostgreSQL console
docker exec -it pgvector-db psql -U ai_user -d prod_support_ai

# Seed the externalized query config table (required before first pipeline run)
INSERT INTO feed_query_config (feed_name, select_clause, from_clause, where_clause, sort_key) VALUES
('INSTRUMENT_FEED',
 'instrument_id, collection_number, business_date, draw_reference_number, instrument_amount, bank_code',
 'instrument_header_staging', 'batch_run_id = :batchRunId', 'instrument_id'),
('ACCOUNTING_FEED',
 'instrument_number, collection_number, accounting_status, debit_amount, credit_amount, bank_code, value_date',
 'accounting_staging', 'batch_run_id = :batchRunId', 'instrument_number');
```

---

## REST API (port 8080)

```bash
# Trigger the full pipeline — returns immediately with runId
curl -X POST http://localhost:8080/api/handoff/pipeline
# → { "runId": "...", "batchRunId": "...", "status": "ACCEPTED" }

# Poll aggregated pipeline status (populationJob + instrumentFeedJob + accountingFeedJob)
curl http://localhost:8080/api/handoff/pipeline/{runId}
# → { "status": "COMPLETED", "stagingStatus": "COMPLETED", "instrumentStatus": "COMPLETED", ... }

# Poll a single Spring Batch job execution by ID
curl http://localhost:8080/api/handoff/status/{jobExecutionId}
# → { "status": "COMPLETED", "exitCode": "COMPLETED", "outputFilePath": "...", ... }
```

---

## Architecture: Three-Job Pipeline

```
POST /api/handoff/pipeline
        │
        ▼ async (@Async)
PipelineJobService.runPipelineAsync()   ← syncJobLauncher chains jobs sequentially
        │
        ├─ 1. populationJob (two sequential steps)
        │       ├── instrumentPopulationStep
        │       │       reader : instrument_header (WHERE instrument_status='ACTIVE')
        │       │       writer : → instrument_header_staging (+ batch_run_id)
        │       └── accountingPopulationStep
        │               reader : accounting_entry (WHERE entry_status='PENDING')
        │               writer : → accounting_staging (+ batch_run_id)
        │
        ├─ 2. instrumentFeedJob
        │       reader : instrument_header_staging  ← query from feed_query_config
        │       processor : Map → HandoffRecord (FixedWidthFormatter)
        │       writer : FeedItemWriterFactory → FIXED_WIDTH .dat file
        │
        └─ 3. accountingFeedJob
                reader : accounting_staging  ← query from feed_query_config
                processor : Map → HandoffRecord (FixedWidthFormatter)
                writer : FeedItemWriterFactory → CSV .csv file
```

**Fail-fast**: if `populationJob` fails, instrument and accounting feed jobs do not run. Same for instrument → accounting.

### Key Components

| Class | Role |
|---|---|
| `FeedProperties` | `@ConfigurationProperties(prefix="handoff")` — all config lives here including `instrumentStaging`, `accountingStaging`, `instrument`, `accounting` sub-configs |
| `PopulationJobConfig` | Defines `populationJob` with two chained steps; uses `FeedItemReader` + `StagingItemWriter` factories |
| `InstrumentFeedJobConfig` / `AccountingFeedJobConfig` | Define feed jobs; reader loads query from `FeedQueryConfigRepository`; writer uses `FeedItemWriterFactory` |
| `SharedBatchConfig` | Two `JobLauncher` beans: `asyncJobLauncher` (REST responses) and `syncJobLauncher` (sequential chaining) |
| `FeedQueryConfigRepository` | `@Component` — reads SELECT/FROM/WHERE/SORT_KEY from `feed_query_config` DB table at step startup; ops can update queries without redeploy |
| `FeedItemWriterFactory` | `@Component` — creates `FlatFileItemWriter<HandoffRecord>` for CSV (comma-join) or FIXED_WIDTH (concat) based on `FeedProperties.Format` |
| `FeedItemReader` | Plain factory class (no `@Component`) — builds `JdbcPagingItemReader` from `DatasourceConfig` |
| `FeedItemProcessor` | `ItemProcessor<Map<String,Object>, HandoffRecord>` — applies `FixedWidthFormatter` per field definition |
| `StagingItemWriter` | Plain factory class — builds `JdbcBatchItemWriter` with dynamic INSERT + `batch_run_id` injection |
| `FixedWidthFormatter` | `@Component`, pure logic: apply format string → truncate → pad per `FieldDefinition` |
| `HandoffRecord` | `LinkedHashMap<String,String>` — insertion order = file column order |
| `PipelineJobService` | `@Service` — orchestrates 3 jobs with `@Async`; tracks state in `ConcurrentHashMap<String, PipelineExecution>` |
| `PipelineExecution` | In-memory VO: holds `runId`, `batchRunId`, per-job statuses and output file paths |

### Database Tables

| Table | Purpose |
|---|---|
| `instrument_header` | Source: instruments awaiting processing |
| `accounting_entry` | Source: accounting entries awaiting processing |
| `instrument_header_staging` | Staging target for population step 1; keyed by `batch_run_id` |
| `accounting_staging` | Staging target for population step 2; keyed by `batch_run_id` |
| `feed_query_config` | Externalized query store (`feed_name` PK, `select_clause`, `from_clause`, `where_clause`, `sort_key`) |

---

## Key Non-Obvious Constraints

### SQL must be split into clauses — never a full string
`SqlPagingQueryProviderFactoryBean` requires separate `SELECT`, `FROM`, `WHERE` clauses.
```yaml
select-clause: "col1, col2"   # no SELECT keyword
from-clause: "table_name"     # no FROM keyword
where-clause: "status = 'ACTIVE'"  # no WHERE keyword; optional
sort-key: col1                # must appear in select-clause and have a DB index
```
A full SQL string silently breaks pagination restart.

### Spring Batch 5 API (Spring Boot 3.x)
`JobBuilderFactory` and `StepBuilderFactory` are **removed**. Always use:
```java
new JobBuilder("name", jobRepository).start(step).build();
new StepBuilder("name", jobRepository).<In, Out>chunk(size, txManager)...build();
```

### FeedItemReader and StagingItemWriter must NOT have @Component
They are instantiated by `PopulationJobConfig`/`InstrumentFeedJobConfig`/`AccountingFeedJobConfig` as `@StepScope` beans. Adding `@Component` causes `BeanDefinitionOverrideException` at startup.

### @StepScope beans — injection only via @Bean method parameters
Reader/writer beans are `@StepScope`. Job parameters resolve via `@Value("#{jobParameters['batchRunId']}")` only in `@Bean` factory methods, never field `@Autowired`.

### Feed queries come from the database, not application.yml
`FeedConfig` has no `datasource` field. `InstrumentFeedJobConfig` and `AccountingFeedJobConfig` call `feedQueryConfigRepository.findByFeedName("INSTRUMENT_FEED")` / `"ACCOUNTING_FEED"` inside the `@StepScope` reader bean. Staging queries (for `populationJob`) still come from `application.yml` under `instrument-staging.source` and `accounting-staging.source`.

### FeedProperties.Format enum controls writer type
`FeedConfig.format` (default `FIXED_WIDTH`) is read by `FeedItemWriterFactory.create(...)`. For CSV, values are comma-joined (no quoting — field values must not contain commas). For FIXED_WIDTH, values are concatenated. The `FixedWidthFormatter` padding/truncation is applied in both cases.

### Field order in HandoffRecord is guaranteed
`HandoffRecord` uses `LinkedHashMap`. `FeedItemProcessor` iterates `FeedConfig.getFields()` in declaration order. **The field order in `application.yml` IS the file column order.** Never use `Map.of()` — it is unordered.

### FixedWidthFormatter truncates from the right
Values longer than `field.length` are silently dropped from the right (mainframe convention).

### Numeric fields need `format`
Without `format: "%.2f"`, `BigDecimal("1234.5").toString()` = `"1234.5"` (no trailing zero). Always set `format` for amount/balance fields before zero-padding.

### StagingItemWriter normalizes Map keys to lowercase
H2 returns column names as `INSTRUMENT_ID` (uppercase). `StagingItemWriter` normalises keys via `k.toLowerCase()` before `MapSqlParameterSource`. Without this, named parameters fail silently on H2.

### Integration tests use H2, not PostgreSQL
`src/test/resources/application-test.yml` → H2 in `MODE=PostgreSQL`. Test table DDL must use `BIGINT GENERATED BY DEFAULT AS IDENTITY` — **not `BIGSERIAL`** (unsupported in H2).

### Integration tests must seed feed_query_config
`FeedQueryConfigRepository` does a live JDBC query at step startup. `HandoffJobIntegrationTest.setUp()` creates the `feed_query_config` table and inserts rows pointing to H2 test staging tables before each test.

### PIT and JaCoCo must NOT run in the same Maven invocation
Bytecode instrumentation conflict. Always two separate commands.

---

## Common Tasks

### Change the query for a feed without redeploying
`UPDATE feed_query_config SET where_clause = '...' WHERE feed_name = 'INSTRUMENT_FEED';`  
No restart needed — `FeedQueryConfigRepository` reads at step startup each run.

### Add a new output field to a feed
1. Add the column to the appropriate `select-clause` in `feed_query_config` (or staging source YAML)
2. Add a `FieldDefinition` entry under `handoff.instrument.fields` or `handoff.accounting.fields` in `application.yml` in the correct position
3. No code changes needed

### Switch a feed from FIXED_WIDTH to CSV
Change `handoff.instrument.format: CSV` (or `accounting`) in `application.yml`. Update `file-suffix` accordingly.

### Tune performance for high volume
Increase `handoff.batch.chunk-size` and `handoff.batch.page-size` (keep them equal). Ensure all sort-key columns have B-tree indexes in PostgreSQL.

### Add a new integration test
Add a method to `HandoffJobIntegrationTest` — it shares the H2 + `@BeforeEach` table setup. Always call `jobRepositoryTestUtils.removeJobExecutions()` in `@BeforeEach`. Use the existing `insertInstrumentStaging()`/`insertAccountingStaging()` helpers.

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DB_USERNAME` | `ai_user` | PostgreSQL username |
| `DB_PASSWORD` | `ai_password` | PostgreSQL password |

---

## Test Coverage

| Layer | Test class | Tests | Type |
|---|---|---|---|
| Formatter | `FixedWidthFormatterTest` | 14 | Unit, parameterized |
| Processor | `FeedItemProcessorTest` | 4 | Unit + Mockito |
| Writer | `HandoffItemWriterTest` | 6 | Unit + spy |
| Writer factory | `FeedItemWriterFactoryTest` | 3 | Unit |
| Query repo | `FeedQueryConfigRepositoryTest` | 2 | Unit + Mockito |
| Properties | `FeedPropertiesTest` | 6 | Unit |
| Domain | `FieldDefinitionTest` | 5 | Unit |
| Service | `PipelineJobServiceTest` | 8 | Unit + Mockito |
| DTOs | `HandoffDtoTest` | 4 | Unit |
| Controller | `HandoffControllerTest` | 5 | `@WebMvcTest` |
| Integration | `HandoffJobIntegrationTest` | 4 | `@SpringBatchTest` + H2 |
| **Total** | | **61** | |

JaCoCo: ≥80% instruction threshold enforced on `mvn verify`. PIT run separately.

---

## Pitfalls to Avoid

- **Never** use `JobBuilderFactory` / `StepBuilderFactory` — removed in Spring Batch 5
- **Never** put a full SQL string in config — split into `select-clause`, `from-clause`, `where-clause`
- **Never** add `@Component` to `FeedItemReader` or `StagingItemWriter` — causes `BeanDefinitionOverrideException`
- **Never** inject `@StepScope` beans via field `@Autowired` — use `@Bean` method parameter injection
- **Never** run PIT and JaCoCo in one Maven command
- **Never** use `BIGSERIAL` in test SQL schemas — use `BIGINT GENERATED BY DEFAULT AS IDENTITY` for H2 compatibility
- **Never** use `String` concatenation with file path separators — use `Path.of(dir, file).toString()`
- **Never** use `Map.of()` to build `HandoffRecord` field maps — unordered; use `putField()` instead
- **Never** add a `datasource` field to `FeedConfig` — feed queries come from `feed_query_config` DB table only
