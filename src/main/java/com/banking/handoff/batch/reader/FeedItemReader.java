package com.banking.handoff.batch.reader;

import java.util.Map;

import javax.sql.DataSource;

import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.SqlPagingQueryProviderFactoryBean;
import org.springframework.jdbc.core.ColumnMapRowMapper;

import com.banking.handoff.config.FeedProperties.DatasourceConfig;
import com.banking.handoff.exception.HandoffException;

public class FeedItemReader {

    public JdbcPagingItemReader<Map<String, Object>> reader(
            String name,
            DataSource dataSource,
            DatasourceConfig ds,
            int pageSize,
            Map<String, Object> parameterValues) {

        SqlPagingQueryProviderFactoryBean factory = new SqlPagingQueryProviderFactoryBean();
        factory.setDataSource(dataSource);
        factory.setSelectClause("SELECT " + ds.getSelectClause());
        factory.setFromClause("FROM " + ds.getFromClause());

        if (ds.getWhereClause() != null && !ds.getWhereClause().isBlank()) {
            factory.setWhereClause("WHERE " + ds.getWhereClause());
        }
        factory.setSortKeys(Map.of(ds.getSortKey(), Order.ASCENDING));

        try {
            JdbcPagingItemReaderBuilder<Map<String, Object>> builder =
                    new JdbcPagingItemReaderBuilder<Map<String, Object>>()
                            .name(name)
                            .dataSource(dataSource)
                            .queryProvider(factory.getObject())
                            .rowMapper(new ColumnMapRowMapper())
                            .pageSize(pageSize);

            if (parameterValues != null && !parameterValues.isEmpty()) {
                builder.parameterValues(parameterValues);
            }

            return builder.build();
        } catch (Exception e) {
            throw new HandoffException("Failed to build item reader: " + name, e);
        }
    }
}
