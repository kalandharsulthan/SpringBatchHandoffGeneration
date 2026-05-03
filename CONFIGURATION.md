# Configuration Reference

All runtime behaviour is driven by `src/main/resources/application.yml`.  
No code changes are needed to reconfigure field layout, SQL query, performance tuning, or output naming.

---

## Full Configuration Structure

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/banking
    username: ${DB_USERNAME:bankinguser}   # override via env var
    password: ${DB_PASSWORD:changeit}      # override via env var
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 30000            # ms

  batch:
    job:
      enabled: false                       # prevent auto-run at startup; use REST API
    jdbc:
      initialize-schema: always            # always=dev/test | never=production

handoff:
  output:
    directory: /data/handoff               # REQUIRED — output directory on filesystem
    file-prefix: HANDOFF_                  # prefix for auto-generated filenames
    file-suffix: .dat                      # suffix for auto-generated filenames
    encoding: UTF-8                        # file character encoding
  batch:
    chunk-size: 1000                       # rows per Spring Batch chunk (transaction boundary)
    page-size: 1000                        # rows per JDBC page fetch
    skip-limit: 10                         # max bad rows skipped before job fails
  datasource:
    select-clause: "col1, col2, col3"      # REQUIRED — columns after SELECT (no SELECT keyword)
    from-clause: "table_name"              # REQUIRED — table/view after FROM (no FROM keyword)
    where-clause: "status = 'ACTIVE'"      # optional — condition after WHERE (no WHERE keyword)
    sort-key: col1                         # REQUIRED — must be in select-clause and DB-indexed
  fields:
    - name: col1                           # REQUIRED — must match a column in select-clause
      length: 20                           # REQUIRED — exact character width in output file
      alignment: LEFT                      # LEFT (pad right) | RIGHT (pad left) — default LEFT
      pad-char: " "                        # padding character — default space
      format:                              # optional Java format string (e.g. "%.2f" for decimals)
```

---

## `handoff.output` — File Output Settings

| Property | Type | Default | Description |
|---|---|---|---|
| `directory` | String | — | **Required.** Absolute path to the output directory. Must exist and be writable. |
| `file-prefix` | String | `HANDOFF_` | Prepended to auto-generated filenames. |
| `file-suffix` | String | `.dat` | Appended to auto-generated filenames. |
| `encoding` | String | `UTF-8` | File character encoding. Use `ISO-8859-1` for legacy mainframe consumers. |

**Auto-generated filename pattern**: `{file-prefix}{yyyyMMdd_HHmmss}{file-suffix}`  
Example: `HANDOFF_20260503_143022.dat`

To use a custom filename, pass it in the request body:
```json
POST /api/handoff/generate
{ "outputFileName": "ACCT_EXTRACT_20260503.dat" }
```
If provided, the full path becomes `{directory}/{outputFileName}`.

---

## `handoff.batch` — Performance Tuning

| Property | Type | Default | Description |
|---|---|---|---|
| `chunk-size` | int | `1000` | Rows processed per Spring Batch chunk. Each chunk is one database transaction. |
| `page-size` | int | `1000` | Rows fetched per JDBC `SELECT ... LIMIT page-size OFFSET n`. |
| `skip-limit` | int | `10` | Maximum rows that can be skipped (due to exceptions) before the job is failed. |

**Recommended**: set `chunk-size == page-size`. This avoids partial page reads mid-transaction.

**High-volume tuning**:
- Increase both to `5000` for throughput; decrease if you get `OutOfMemoryError`
- HikariCP `maximum-pool-size` should be at least 2 (one for the step datasource, one for Spring Batch metadata writes)
- The `sort-key` column must have a B-tree index in PostgreSQL — without it, paging degrades to O(n²)

---

## `handoff.datasource` — SQL Query Configuration

| Property | Type | Required | Description |
|---|---|---|---|
| `select-clause` | String | Yes | Column list — everything after `SELECT` up to `FROM`. No `SELECT` keyword. |
| `from-clause` | String | Yes | Table/view name — everything after `FROM` up to `WHERE`. No `FROM` keyword. |
| `where-clause` | String | No | Filter condition — everything after `WHERE`. No `WHERE` keyword. |
| `sort-key` | String | Yes | Column used for `ORDER BY` in paging SQL. Must appear in `select-clause`. |

**Why split clauses?** `SqlPagingQueryProviderFactoryBean` constructs the paging SQL itself (e.g., `LIMIT/OFFSET` for PostgreSQL). It needs the clauses separately to inject the `ORDER BY` and pagination logic. Providing a full SQL string is not supported.

**Generated SQL example**:
```sql
-- page 1
SELECT account_no, customer_name, balance
FROM accounts
WHERE status = 'ACTIVE'
ORDER BY account_no ASC
LIMIT 1000 OFFSET 0

-- page 2
LIMIT 1000 OFFSET 1000
```

**Joins and subqueries** are supported — put them in `from-clause`:
```yaml
from-clause: "accounts a JOIN customers c ON a.customer_id = c.id"
```

**Multiple conditions**:
```yaml
where-clause: "status = 'ACTIVE' AND branch_code = '001'"
```

---

## `handoff.fields` — Field Definition List

Fields are processed in the order declared. **The declaration order = output file column order.**

| Property | Type | Required | Default | Description |
|---|---|---|---|---|
| `name` | String | Yes | — | Must exactly match a column name returned by `select-clause`. Case-sensitive. |
| `length` | int | Yes | — | Fixed character width of this field in the output file. |
| `alignment` | Enum | No | `LEFT` | `LEFT`: value left-aligned, padded on right. `RIGHT`: value right-aligned, padded on left. |
| `pad-char` | char | No | ` ` (space) | Character used for padding. Use `"0"` for numeric fields. |
| `format` | String | No | — | Java `String.format` pattern applied before padding. Use `"%.2f"` for decimal numbers. |

### Field Formatting Rules (applied in order)

1. **Null input** → treated as empty string `""`
2. **Format pattern** → if `format` is set, `String.format(format, rawValue)` is called
3. **Truncation** → if result length > `length`, characters beyond `length` are dropped from the right
4. **Padding**:
   - `LEFT` alignment → value + padding characters appended on right
   - `RIGHT` alignment → padding characters prepended on left + value

### Field Examples

```yaml
# Text field: left-aligned, space-padded
- name: account_no
  length: 20
  alignment: LEFT
  pad-char: " "
# Input "ACC001" → "ACC001              " (14 spaces)

# Numeric field: right-aligned, zero-padded, 2 decimal places
- name: balance
  length: 15
  alignment: RIGHT
  pad-char: "0"
  format: "%.2f"
# Input BigDecimal(1234.56) → "000000001234.56"

# Integer field: right-aligned, zero-padded
- name: sequence_no
  length: 8
  alignment: RIGHT
  pad-char: "0"
# Input 42 → "00000042"

# Date field: format as YYYYMMDD then left-align
- name: value_date
  length: 8
  alignment: LEFT
  pad-char: " "
  format: "%tY%<tm%<td"
# Input java.util.Date → "20260503"

# Long text truncated to fit
- name: description
  length: 30
  alignment: LEFT
  pad-char: " "
# Input "This is a very long description text" → "This is a very long descriptio" (truncated)
```

---

## Environment Variables

| Variable | Mapped to | Default | Notes |
|---|---|---|---|
| `DB_USERNAME` | `spring.datasource.username` | `bankinguser` | PostgreSQL login |
| `DB_PASSWORD` | `spring.datasource.password` | `changeit` | PostgreSQL password |

---

## Production Checklist

Before deploying to production, update `application.yml`:

```yaml
spring:
  batch:
    jdbc:
      initialize-schema: never    # schema was created once manually

  datasource:
    hikari:
      maximum-pool-size: 20       # tune based on load

handoff:
  output:
    directory: /app/handoff/output  # production mount path
  batch:
    chunk-size: 5000              # tune for throughput
    page-size: 5000
```

Also verify:
- Output `directory` exists and the process user has write permission
- `sort-key` column has a B-tree index
- Spring Batch schema tables (`BATCH_*`) are present in the database (run `schema-postgresql.sql` from `spring-batch-core` JAR)
- `spring.batch.job.enabled=false` remains set — job must only run via REST API, not at startup

---

## Test Configuration (`application-test.yml`)

Used automatically when tests run with `@ActiveProfiles("test")`.

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;NON_KEYWORDS=VALUE
    driver-class-name: org.h2.Driver
    username: sa
    password: ""
  batch:
    jdbc:
      initialize-schema: always

handoff:
  output:
    directory: ${java.io.tmpdir}/handoff-test
  datasource:
    select-clause: "account_no, customer_name, balance"
    from-clause: "test_accounts"
    where-clause: "status = 'ACTIVE'"
    sort-key: account_no
  batch:
    chunk-size: 10
    page-size: 10
```

H2 runs in PostgreSQL compatibility mode. The `test_accounts` table is created and populated in `HandoffJobIntegrationTest.@BeforeEach`.  
**DDL note**: use `BIGINT AUTO_INCREMENT` — do not use `BIGSERIAL` (PostgreSQL-only syntax not supported by H2).
