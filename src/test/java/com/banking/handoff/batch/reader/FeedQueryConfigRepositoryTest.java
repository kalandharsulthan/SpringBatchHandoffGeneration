package com.banking.handoff.batch.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.banking.handoff.config.FeedProperties;

@ExtendWith(MockitoExtension.class)
class FeedQueryConfigRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private FeedQueryConfigRepository repository;

    @BeforeEach
    void setUp() {
        repository = new FeedQueryConfigRepository(jdbcTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnDatasourceConfigForKnownFeedName() {
        FeedProperties.DatasourceConfig expected = new FeedProperties.DatasourceConfig();
        expected.setSelectClause("instrument_id, amount");
        expected.setFromClause("instrument_header_staging");
        expected.setWhereClause("batch_run_id = :batchRunId");
        expected.setSortKey("instrument_id");

        doReturn(expected).when(jdbcTemplate)
                .queryForObject(anyString(), (RowMapper<FeedProperties.DatasourceConfig>) org.mockito.ArgumentMatchers.any(RowMapper.class), eq("INSTRUMENT_FEED"));

        FeedProperties.DatasourceConfig result = repository.findByFeedName("INSTRUMENT_FEED");

        assertThat(result.getSelectClause()).isEqualTo("instrument_id, amount");
        assertThat(result.getFromClause()).isEqualTo("instrument_header_staging");
        assertThat(result.getWhereClause()).isEqualTo("batch_run_id = :batchRunId");
        assertThat(result.getSortKey()).isEqualTo("instrument_id");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPassFeedNameAsQueryParameter() {
        FeedProperties.DatasourceConfig config = new FeedProperties.DatasourceConfig();
        config.setSelectClause("instrument_number");
        config.setFromClause("accounting_staging");
        config.setSortKey("instrument_number");

        doReturn(config).when(jdbcTemplate)
                .queryForObject(anyString(), (RowMapper<FeedProperties.DatasourceConfig>) org.mockito.ArgumentMatchers.any(RowMapper.class), eq("ACCOUNTING_FEED"));

        FeedProperties.DatasourceConfig result = repository.findByFeedName("ACCOUNTING_FEED");

        assertThat(result.getFromClause()).isEqualTo("accounting_staging");
    }
}
