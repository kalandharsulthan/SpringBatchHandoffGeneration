# Configuration Reference

All runtime behaviour is driven by `src/main/resources/application.yml`.  
No code changes are needed to reconfigure field layout, performance tuning, or output naming.  
Feed generation SQL queries are stored in the `feed_query_config` database table and can be updated at runtime without redeployment.

---

## Full Configuration Structure

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/prod_support_ai
    username: ${DB_USERNAME:ai_user}
    password: ${DB_PASSWORD:ai_password}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 30000

  batch:
    job:
      enabled: false          # MUST remain false — prevents auto-run at startup
    jdbc:
      initialize-schema: always  # always=dev/test | never=production

handoff:
  output:
    directory: /tmp/handoff-output   # shared output root; individual feeds use sub-config
    encoding: UTF-8

  batch:
    chunk-size: 1000
    page-size: 1000
    skip-limit: 10

  instrument-staging:              # Population Job Step 1: instrument_header → staging
    table-name: instrument_header_staging
    source:
      select-clause: "instrument_id, collection_number, ..."
      from-clause: "instrument_header"
      where-clause: "instrument_status = 'ACTIVE'"
      sort-key: instrument_id
    columns:
      - instrument_id
      - collection_number
      # ... (must match select-clause output columns exactly)

  accounting-staging:              # Population Job Step 2: accounting_entry → staging
    table-name: accounting_staging
    source:
      select-clause: "instrument_number, collection_number, ..."
      from-clause: "accounting_entry"
      where-clause: "entry_status = 'PENDING'"
      sort-key: instrument_number
    columns:
      - instrument_number
      # ...

  instrument:                      # Instrument Feed Job
    enabled: true                  # set false to skip this job entirely
    format: FIXED_WIDTH            # FIXED_WIDTH | CSV
    output:
      file-prefix: INSTRUMENT_FEED_
      file-suffix: .dat
    fields:
      - name: instrument_id
        length: 20
        alignment: LEFT
        pad-char: " "
      - name: instrument_amount
        length: 15
        alignment: RIGHT
        pad-char: "0"
        format: "%.2f"
      # ...

  accounting:                      # Accounting Feed Job
    enabled: true
    format: CSV                    # FIXED_WIDTH | CSV
    output:
      file-prefix: ACCOUNTING_FEED_
      file-suffix: .csv
    fields:
      - name: instrument_number
        length: 20
        alignment: LEFT
        pad-char: " "
      # ...
```

---

## `handoff.output` — Shared Output Settings

| Property | Type | Default | Description |
|---|---|---|---|
| `directory` | String | — | **Required.** Absolute path to the output root. Must exist and be writable. |
| `encoding` | String | `UTF-8` | File character encoding applied to all feed output files. Use `ISO-8859-1` for legacy mainframe consumers. |

---

## `handoff.batch` — Performance Tuning

| Property | Type | Default | Description |
|---|---|---|---|
| `chunk-size` | int | `1000` | Rows per Spring Batch chunk (one DB transaction boundary). |
| `page-size` | int | `1000` | Rows per JDBC `SELECT ... LIMIT page-size`. |
| `skip-limit` | int | `10` | Max bad rows skipped before the step fails. |

**Recommended**: keep `chunk-size == page-size`. HikariCP `maximum-pool-size` must be at least 2 (one for the step datasource, one for Spring Batch metadata writes). The `sort-key` column must have a B-tree index — without it, paging degrades to O(n²).

---

## `handoff.instrument-staging` and `handoff.accounting-staging` — Population Job

Used by `populationJob` to copy source data into staging tables. Not related to feed query configuration.

| Property | Type | Description |
|---|---|---|
| `table-name` | String | Target staging table name |
| `source.select-clause` | String | Columns to read (no `SELECT` keyword) |
| `source.from-clause` | String | Source table/view (no `FROM` keyword) |
| `source.where-clause` | String | Optional filter (no `WHERE` keyword) |
| `source.sort-key` | String | Must appear in `select-clause` and be DB-indexed |
| `columns` | List | Column names inserted into the staging table; must match the aliased names in `select-clause` |

Each run is isolated by a `batch_run_id` UUID automatically injected into every staging row.

---

## `feed_query_config` Table — Externalized Feed Queries

The instrument and accounting feed readers do **not** use `application.yml` for their queries. They read from this database table at step startup each run.

```sql
CREATE TABLE feed_query_config (
    feed_name      VARCHAR(50)  NOT NULL PRIMARY KEY,
    select_clause  TEXT         NOT NULL,
    from_clause    TEXT         NOT NULL,
    where_clause   TEXT,
    sort_key       VARCHAR(50)  NOT NULL,
    created_date   TIMESTAMP    DEFAULT now()
);
```

| feed_name | Consumed by |
|---|---|
| `INSTRUMENT_FEED` | `instrumentFeedJob` reader |
| `ACCOUNTING_FEED` | `accountingFeedJob` reader |

To change a query without redeployment:
```sql
UPDATE feed_query_config
SET where_clause = 'batch_run_id = :batchRunId AND bank_code = ''BANKX'''
WHERE feed_name = 'INSTRUMENT_FEED';
```

**Why split clauses?** `SqlPagingQueryProviderFactoryBean` constructs the paging SQL itself and needs clauses separately to inject `ORDER BY` and `LIMIT/OFFSET`. A full SQL string is not supported.

---

## `handoff.instrument` and `handoff.accounting` — Feed Job Config

| Property | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Set `false` to skip this job; pipeline continues to the next |
| `format` | Enum | `FIXED_WIDTH` | `FIXED_WIDTH` (fields concatenated) or `CSV` (fields comma-separated) |
| `output.file-prefix` | String | `FEED_` | Prepended to the generated filename |
| `output.file-suffix` | String | `.dat` | Appended to the generated filename |
| `fields` | List | — | Field definitions — see below |

**Generated filename pattern**: `{file-prefix}{yyyyMMdd_HHmmss}_{8-char batchRunId}{file-suffix}`

### Field Definitions

| Property | Type | Required | Default | Description |
|---|---|---|---|---|
| `name` | String | Yes | — | Must exactly match a column name returned by `select_clause` in `feed_query_config`. Case-sensitive. |
| `length` | int | Yes | — | Fixed character width in the output file. |
| `alignment` | Enum | No | `LEFT` | `LEFT`: value left-aligned, pad on right. `RIGHT`: value right-aligned, pad on left. |
| `pad-char` | char | No | ` ` (space) | Padding character. Use `"0"` for numeric fields. |
| `format` | String | No | — | Java `String.format` pattern applied before padding (e.g. `"%.2f"` for decimal numbers). |

**Field formatting pipeline** (applied in order):
1. Null input → treated as `""`
2. `format` is set → `String.format(format, rawValue)` applied
3. Result longer than `length` → right-truncated
4. Padding applied (left or right depending on alignment)

**Declaration order = output column order.** Do not use `Map.of()` for field maps — it is unordered.

### Field Examples

```yaml
# Text field: left-aligned, space-padded
- name: instrument_id
  length: 20
  alignment: LEFT
  pad-char: " "
# Input "INSTR-001" → "INSTR-001           " (11 spaces)

# Amount field: right-aligned, zero-padded, 2 decimal places
- name: instrument_amount
  length: 15
  alignment: RIGHT
  pad-char: "0"
  format: "%.2f"
# Input BigDecimal(15000) → "000000015000.00"

# Date string field: no format needed if already a VARCHAR in DB
- name: business_date
  length: 10
  alignment: LEFT
  pad-char: " "
# Input "2026-05-09" → "2026-05-09" (exact fit)
```

---

## Environment Variables

| Variable | Mapped to | Default | Notes |
|---|---|---|---|
| `DB_USERNAME` | `spring.datasource.username` | `ai_user` | PostgreSQL login |
| `DB_PASSWORD` | `spring.datasource.password` | `ai_password` | PostgreSQL password |

Any `application.yml` key can also be overridden at runtime:
```bash
java -jar target/handoff-generation-1.0.0-SNAPSHOT.jar \
  --handoff.batch.chunk-size=5000 \
  --handoff.output.directory=/mnt/nas/handoff
```

---

## Production Checklist

```yaml
spring:
  batch:
    jdbc:
      initialize-schema: never    # schema created once manually via schema-postgresql.sql
  datasource:
    hikari:
      maximum-pool-size: 20

handoff:
  output:
    directory: /app/handoff/output
  batch:
    chunk-size: 5000
    page-size: 5000
```

Also verify:
- Output `directory` exists and process user has write permission
- All `sort-key` columns have B-tree indexes in PostgreSQL
- Spring Batch schema tables (`BATCH_*`) are present
- `feed_query_config` table exists and contains `INSTRUMENT_FEED` and `ACCOUNTING_FEED` rows
- `spring.batch.job.enabled=false` remains set

---

## Test Configuration (`application-test.yml`)

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;NON_KEYWORDS=VALUE
    driver-class-name: org.h2.Driver
    username: sa
    password: ""

handoff:
  output:
    directory: ${java.io.tmpdir}/handoff-test
  batch:
    chunk-size: 10
    page-size: 10
    skip-limit: 5
  instrument-staging:
    table-name: test_instrument_header_staging
    source:
      from-clause: "test_instrument_header"
      # ...
  accounting-staging:
    table-name: test_accounting_staging
    source:
      from-clause: "test_accounting_entry"
      # ...
```

Tables are created and `feed_query_config` is seeded in `HandoffJobIntegrationTest.@BeforeEach`.  
**DDL note**: use `BIGINT GENERATED BY DEFAULT AS IDENTITY` — **not `BIGSERIAL`** (unsupported in H2).
