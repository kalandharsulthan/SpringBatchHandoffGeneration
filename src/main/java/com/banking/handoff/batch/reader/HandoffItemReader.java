package com.banking.handoff.batch.reader;

import java.util.Map;

import javax.sql.DataSource;

import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.SqlPagingQueryProviderFactoryBean;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import com.banking.handoff.config.HandoffProperties;
import com.banking.handoff.exception.HandoffException;

public class HandoffItemReader {

    public JdbcPagingItemReader<Map<String, Object>> reader(
            DataSource dataSource, HandoffProperties properties) {

        HandoffProperties.Datasource ds = properties.getDatasource();

        SqlPagingQueryProviderFactoryBean providerFactory = new SqlPagingQueryProviderFactoryBean();
        providerFactory.setDataSource(dataSource);
        providerFactory.setSelectClause("SELECT " + ds.getSelectClause());
        providerFactory.setFromClause("FROM " + ds.getFromClause());

        if (ds.getWhereClause() != null && !ds.getWhereClause().isBlank()) {
            providerFactory.setWhereClause("WHERE " + ds.getWhereClause());
        }

        providerFactory.setSortKeys(Map.of(ds.getSortKey(), Order.ASCENDING));

        try {
            return new JdbcPagingItemReaderBuilder<Map<String, Object>>()
                    .name("handoffItemReader")
                    .dataSource(dataSource)
                    .queryProvider(providerFactory.getObject())
                    .rowMapper(new ColumnMapRowMapper())
                    .pageSize(properties.getBatch().getPageSize())
                    .build();
        } catch (Exception e) {
            throw new HandoffException("Failed to build paging item reader", e);
        }
    }
}
