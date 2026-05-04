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
| `RUNBOOK.md` | Step-by-step setup, run, API usage, runtime parameters, DB console, troubleshooting |
| `ARCHITECTURE.md` | Data flow diagrams, component map, batch pipeline internals |
| `CONFIGURATION.md` | Full `application.yml` property reference with examples |

---

## Build Commands

```bash
# Set Java 17 (required — system may default to a different version)
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home

# Full build: compile + all tests + JaCoCo coverage check (≥80%)
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
- DB: `prod_support_ai`  |  User: `ai_user`  |  Password: `ai_password`  |  Port: `5432`
- Output directory: `/tmp/handoff-output`

---

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
│   ├── reader/HandoffItemReader.java     JdbcPagingItemReader factory (no @Component)
│   ├── processor/HandoffItemProcessor.java  Map<String,Object> → HandoffRecord
│   └── writer/HandoffItemWriter.java     Delegating wrapper (no @Component)
├── domain/
│   ├── FieldDefinition.java              name, length, alignment, padChar, format
│   └── HandoffRecord.java                LinkedHashMap<String,String> — field order preserved
├── util/
│   └── FixedWidthFormatter.java          Pure Java pad/truncate — no Spring context
└── exception/
    └── HandoffException.java

src/test/java/com/banking/handoff/
├── util/FixedWidthFormatterTest.java         14 parameterized cases
├── batch/processor/HandoffItemProcessorTest  4 cases
├── batch/writer/HandoffItemWriterTest        6 cases (includes Mockito spy)
├── config/HandoffPropertiesTest              6 cases
├── domain/FieldDefinitionTest                5 cases
├── service/HandoffJobServiceTest             11 cases
├── service/dto/HandoffDtoTest                4 cases
├── controller/HandoffControllerTest          5 cases (@WebMvcTest)
└── integration/HandoffJobIntegrationTest     4 cases (@SpringBatchTest + H2)
```

---

## Key Non-Obvious Constraints

### SQL query must be split into clauses — not a single string
`SqlPagingQueryProviderFactoryBean` requires separate `SELECT`, `FROM`, `WHERE` clauses.
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

### @Component must NOT be on batch components
`HandoffItemReader` and `HandoffItemWriter` do NOT have `@Component`. They are instantiated by `BatchJobConfig` as `@StepScope` beans. Adding `@Component` causes `BeanDefinitionOverrideException` at startup.

### @StepScope beans
`handoffItemReader` and `handoffItemWriter` are `@StepScope` — created per step execution. Always inject via `@Bean` method parameters in `BatchJobConfig`, never via `@Autowired` field injection. The `outputFilePath` job parameter is resolved via `@Value("#{jobParameters['outputFilePath']}")`.

### Field order in HandoffRecord is guaranteed
`HandoffRecord` uses `LinkedHashMap` internally. `HandoffItemProcessor` iterates `HandoffProperties.getFields()` in declaration order, which matches the output file column order. **The field order in `application.yml` IS the file column order.**

### FixedWidthFormatter truncates from the right
If a value is longer than `field.length`, characters beyond the limit are silently dropped from the right. Standard banking mainframe behaviour.

### Numeric fields need `format`
Without a `format` pattern, `BigDecimal("1234.5").toString()` = `"1234.5"` (no trailing zero). Always set `format: "%.2f"` for balance/amount fields to ensure consistent decimal places before zero-padding.

### PIT and JaCoCo must NOT run in the same Maven invocation
Bytecode instrumentation conflict. CI must use two separate stages:
1. `mvn clean verify` — JaCoCo
2. `mvn pitest:mutationCoverage` — PIT (unit tests only; integration excluded)

### Integration tests use H2, not PostgreSQL
`src/test/resources/application-test.yml` → H2 in `MODE=PostgreSQL`. Test DDL must use `BIGINT AUTO_INCREMENT`, not `BIGSERIAL`.

---

## Common Tasks

### Add a new output field
1. Add the column to `handoff.datasource.select-clause` in `application.yml`
2. Add a `FieldDefinition` entry under `handoff.fields` in the correct position
3. No code changes needed

### Change output directory or file naming
Edit `handoff.output.directory`, `file-prefix`, `file-suffix` in `application.yml`. No code changes needed.

### Tune performance for high volume
Increase `handoff.batch.chunk-size` and `handoff.batch.page-size`. Ensure the sort-key column has a DB index.

### Connect to PostgreSQL console
```bash
docker exec -it pgvector-db psql -U ai_user -d prod_support_ai
```

### Add a new test
- Unit tests: no Spring context needed for `FixedWidthFormatter`, domain, and DTO classes
- Integration tests: add a method to `HandoffJobIntegrationTest` — it shares the H2 + `@BeforeEach` setup
- Always call `jobRepositoryTestUtils.removeJobExecutions()` in `@BeforeEach` for integration tests

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DB_USERNAME` | `ai_user` | PostgreSQL username |
| `DB_PASSWORD` | `ai_password` | PostgreSQL password |

---

## Test Coverage Results (actual)

| Layer | Test class | Tests | Type |
|---|---|---|---|
| Formatter | `FixedWidthFormatterTest` | 14 | Unit, parameterized |
| Processor | `HandoffItemProcessorTest` | 4 | Unit + Mockito |
| Writer | `HandoffItemWriterTest` | 6 | Unit + spy |
| Properties | `HandoffPropertiesTest` | 6 | Unit |
| Domain | `FieldDefinitionTest` | 5 | Unit |
| Service | `HandoffJobServiceTest` | 11 | Unit + Mockito |
| DTOs | `HandoffDtoTest` | 4 | Unit |
| Controller | `HandoffControllerTest` | 5 | `@WebMvcTest` |
| Integration | `HandoffJobIntegrationTest` | 4 | `@SpringBatchTest` + H2 |
| **Total** | | **59** | |

| Coverage metric | Result |
|---|---|
| JaCoCo instruction | **97%** (threshold: 80%) |
| JaCoCo branch | **85%** |
| JaCoCo class | **100%** |
| PIT mutation score | **97%** (72/74 killed) |

---

## Pitfalls to Avoid

- **Never** use `JobBuilderFactory` / `StepBuilderFactory` — removed in Spring Batch 5
- **Never** put a full SQL string in config — split into `select-clause`, `from-clause`, `where-clause`
- **Never** add `@Component` to `HandoffItemReader` or `HandoffItemWriter` — causes `BeanDefinitionOverrideException`
- **Never** inject `@StepScope` beans via field `@Autowired` — use `@Bean` method parameter injection
- **Never** run PIT and JaCoCo together in one Maven command
- **Never** use `BIGSERIAL` in test SQL schemas — use `BIGINT AUTO_INCREMENT` for H2 compatibility
- **Never** use `String` concatenation with file path separators — use `Path.of(dir, file).toString()`
- **Never** use `Map.of()` to build `HandoffRecord` field maps — unordered; use `putField()` instead
