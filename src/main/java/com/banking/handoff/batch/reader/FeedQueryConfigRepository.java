package com.banking.handoff.batch.reader;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.banking.handoff.config.FeedProperties;

@Component
public class FeedQueryConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public FeedQueryConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public FeedProperties.DatasourceConfig findByFeedName(String feedName) {
        return jdbcTemplate.queryForObject(
                "SELECT select_clause, from_clause, where_clause, sort_key " +
                "FROM feed_query_config WHERE feed_name = ?",
                (rs, rowNum) -> {
                    FeedProperties.DatasourceConfig config = new FeedProperties.DatasourceConfig();
                    config.setSelectClause(rs.getString("select_clause"));
                    config.setFromClause(rs.getString("from_clause"));
                    config.setWhereClause(rs.getString("where_clause"));
                    config.setSortKey(rs.getString("sort_key"));
                    return config;
                },
                feedName);
    }
}
