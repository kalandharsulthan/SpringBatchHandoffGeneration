package com.banking.handoff.batch.writer;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

public class StagingItemWriter {

    public JdbcBatchItemWriter<Map<String, Object>> writer(
            DataSource dataSource,
            String tableName,
            List<String> columns,
            String batchRunId) {

        String columnList = "batch_run_id, " + String.join(", ", columns);
        String paramList = ":batchRunId, " + columns.stream()
                .map(c -> ":" + c)
                .collect(Collectors.joining(", "));

        String sql = "INSERT INTO " + tableName + " (" + columnList + ") VALUES (" + paramList + ")";

        return new JdbcBatchItemWriterBuilder<Map<String, Object>>()
                .sql(sql)
                .dataSource(dataSource)
                .itemSqlParameterSourceProvider(item -> {
                    MapSqlParameterSource params = new MapSqlParameterSource();
                    // Normalize keys to lowercase; ColumnMapRowMapper returns LinkedCaseInsensitiveMap
                    // but MapSqlParameterSource copies into a case-sensitive map, so :account_no
                    // won't match ACCOUNT_NO (which H2 returns for unquoted identifiers).
                    item.forEach((k, v) -> params.addValue(k.toLowerCase(), v));
                    params.addValue("batchRunId", batchRunId);
                    return params;
                })
                .build();
    }
}
