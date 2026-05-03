# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Project Overview

Spring Batch 5 / Spring Boot 3.3.x handoff file generation service for banking.
Reads data from PostgreSQL via configurable SQL query, formats rows as **fixed-width text**, and writes files to the local filesystem. Triggered via REST API (async — POST returns immediately, client polls status).

**Stack**: Java 17, Spring Boot 3.3.x, Spring Batch 5, PostgreSQL, Maven  
**Package root**: `com.banking.handoff`

---

## Documentation

| File | Purpose |
|---|---|
| `RUNBOOK.md` | Step-by-step setup, run, API usage, runtime parameters, troubleshooting |
| `ARCHITECTURE.md` | Data flow diagrams, component map, batch pipeline internals |
| `CONFIGURATION.md` | Full `application.yml` property reference with examples |

## Build Commands

```bash
# Full build: compile + all tests + JaCoCo coverage check (≥80% instruction coverage)
mvn clean verify

# Tests only (skip coverage enforcement)
mvn test

# Mutation testing — run SEPARATELY, not during normal verify
mvn pitest:mutationCoverage

# Single test class
mvn test -Dtest=FixedWidthFormatterTest
mvn test -Dtest=HandoffJobIntegrationTest

# Skip coverage threshold for quick local iteration
mvn clean install -Djacoco.minimum.coverage=0
```

## Run Locally

```bash
export DB_USERNAME=bankinguser
export DB_PASSWORD=changeit
mvn spring-boot:run
```

Requires a running PostgreSQL instance. See `src/main/resources/application.yml` for DB URL.

## REST API (port 8080)

```bash
# Trigger file generation — returns immediately with jobExecutionId
curl -X POST http://localhost:8080/api/handoff/generate \
  -H "Content-Type: application/json" \
  -d '{"outputFileName": "HANDOFF_20260503.dat"}'
# → { "jobExecutionId": 1, "status": "STARTING" }

# Poll job status
curl http://localhost:8080/api/handoff/status/1
# → { "status": "COMPLETED", "exitCode": "COMPLETED", "outputFilePath": "...", ... }
```

---

## Project Layout

```
src/main/java/com/banking/handoff/
├── HandoffGenerationApplication.java     entry point
├── config/
│   ├── HandoffProperties.java            @ConfigurationProperties — all config lives here
│   └── BatchJobConfig.java               Job, Step, reader/writer beans
├── controller/
│   └── HandoffController.java            POST /api/handoff/generate, GET /api/handoff/status/{id}
├── service/
│   ├── HandoffJobService.java            JobLauncher orchestration, status lookup
│   └── dto/                              HandoffJobRequest, HandoffJobResponse, HandoffStatusResponse
├── batch/
│   ├── reader/HandoffItemReader.java     JdbcPagingItemReader factory
│   ├── processor/HandoffItemProcessor.java  Map<String,Object> → HandoffRecord
│   └── writer/HandoffItemWriter.java     Delegating wrapper (tracks record count)
├── domain/
│   ├── FieldDefinition.java              name, length, alignment, padChar, format
│   └── HandoffRecord.java                LinkedHashMap<String,String> — field order preserved
├── util/
│   └── FixedWidthFormatter.java          Pure Java pad/truncate — no Spring context
└── exception/
    └── HandoffException.java
```

---

## Key Non-Obvious Constraints

### SQL query must be split into clauses — not a single string
`SqlPagingQueryProviderFactoryBean` requires separate `SELECT`, `FROM`, `WHERE` clauses.
Configure in `application.yml` as:
```yaml
handoff.datasource:
  select-clause: "col1, col2, col3"   # no SELECT keyword
  from-clause: "table_name"            # no FROM keyword
  where-clause: "status = 'ACTIVE'"   # no WHERE keyword; optional
  sort-key: col1                       # must be in SELECT and indexed in DB
```
Do NOT put a full SQL string here — it will silently break pagination restart.

### Spring Batch 5 API (Spring Boot 3.x)
`JobBuilderFactory` and `StepBuilderFactory` are **removed**. Always use:
```java
new JobBuilder("name", jobRepository).start(step).build();
new StepBuilder("name", jobRepository).<In, Out>chunk(size, txManager)...build();
```
Never copy Spring Batch 4 examples from Stack Overflow into this project.

### @StepScope beans
`handoffItemReader` and `handoffItemWriter` are `@StepScope` beans — they are created per step execution. Always inject them via `@Bean` method parameters in `BatchJobConfig`, never via `@Autowired` field injection. The `outputFilePath` job parameter is resolved via `@Value("#{jobParameters['outputFilePath']}")`.

### Field order in HandoffRecord is guaranteed
`HandoffRecord` uses `LinkedHashMap` internally. `HandoffItemProcessor` iterates `HandoffProperties.getFields()` in declaration order, which matches the output file column order. The field order in `application.yml` IS the file column order.

### FixedWidthFormatter truncates from the right
If a value is longer than `field.length`, characters beyond the limit are silently dropped from the right. This is standard banking mainframe behaviour. Do not change without coordinating with downstream consumers.

### Balance / numeric fields need `format`
Without a `format` pattern, a `BigDecimal` value like `1234.56` becomes `"1234.56"` via `toString()`. For zero-padded right-aligned fields (e.g., length 15), that produces `"0000000001234.56"` only if `format: "%.2f"` is set. Always set `format` for decimal fields.

### PIT and JaCoCo must NOT run in the same Maven invocation
Running both in a single `mvn test` causes bytecode instrumentation conflicts. CI should have two separate pipeline stages:
1. `mvn clean verify` — runs JaCoCo
2. `mvn pitest:mutationCoverage` — runs PIT (unit tests only; integration tests are excluded)

### Integration tests use H2, not PostgreSQL
`src/test/resources/application-test.yml` switches to H2 in `MODE=PostgreSQL`. The test DDL must use `BIGINT AUTO_INCREMENT` (H2) not `BIGSERIAL` (PostgreSQL). Do not use `SERIAL` or `GENERATED ALWAYS AS IDENTITY` in test SQL.

---

## Common Tasks

### Add a new output field
1. Add a column to the SQL `select-clause` in `application.yml`
2. Add a new `FieldDefinition` entry under `handoff.fields` in the correct position
3. No code changes needed

### Change output directory or file naming
Edit `handoff.output.directory`, `file-prefix`, `file-suffix` in `application.yml`. No code changes needed.

### Tune performance for high volume
Increase `handoff.batch.chunk-size` and `handoff.batch.page-size` in `application.yml`. The sort-key column must have a database index — without it, each page causes a full table scan.

### Add a new test
- Unit tests: extend existing test classes or add to the same package, no Spring context needed for `FixedWidthFormatter`
- Integration tests: add a method to `HandoffJobIntegrationTest` — it shares the H2 setup
- Always call `jobRepositoryTestUtils.removeJobExecutions()` in `@BeforeEach` for integration tests

---

## Environment Variables

| Variable      | Default       | Description            |
|---------------|---------------|------------------------|
| `DB_USERNAME` | `bankinguser` | PostgreSQL username    |
| `DB_PASSWORD` | `changeit`    | PostgreSQL password    |

---

## Test Coverage Goals

| Layer              | Test class                       | Type                  |
|--------------------|----------------------------------|-----------------------|
| Formatter          | `FixedWidthFormatterTest`        | Unit, parameterized   |
| Processor          | `HandoffItemProcessorTest`       | Unit + Mockito        |
| Writer             | `HandoffItemWriterTest`          | Unit + `@TempDir`     |
| Service            | `HandoffJobServiceTest`          | Unit + Mockito        |
| Controller         | `HandoffControllerTest`          | `@WebMvcTest`         |
| End-to-end         | `HandoffJobIntegrationTest`      | `@SpringBatchTest` + H2 |

JaCoCo minimum: **80% instruction coverage** (enforced on `mvn verify`).  
`HandoffGenerationApplication` is excluded from the JaCoCo check.

---

## Pitfalls to Avoid

- **Never** use `JobBuilderFactory` / `StepBuilderFactory` — removed in Spring Batch 5
- **Never** put a full SQL string in `handoff.datasource.query` — the property doesn't exist; split into clauses
- **Never** inject `@StepScope` beans via field `@Autowired` — use method parameter injection in `@Configuration`
- **Never** run PIT and JaCoCo together in one Maven command
- **Never** use `BIGSERIAL` in test SQL schemas — use `BIGINT AUTO_INCREMENT` for H2 compatibility
- **Never** use `String` concatenation with file path separators — use `Path.of(dir, file).toString()`
