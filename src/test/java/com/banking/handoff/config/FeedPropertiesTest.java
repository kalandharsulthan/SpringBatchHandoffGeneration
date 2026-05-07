package com.banking.handoff.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.banking.handoff.domain.FieldDefinition;

class FeedPropertiesTest {

    @Test
    void shouldReturnOutputConfig() {
        FeedProperties.Output output = new FeedProperties.Output();
        output.setDirectory("/tmp/handoff");
        output.setEncoding("ISO-8859-1");

        FeedProperties props = new FeedProperties();
        props.setOutput(output);

        assertThat(props.getOutput().getDirectory()).isEqualTo("/tmp/handoff");
        assertThat(props.getOutput().getEncoding()).isEqualTo("ISO-8859-1");
    }

    @Test
    void shouldHaveDefaultOutputValues() {
        FeedProperties.Output output = new FeedProperties.Output();
        assertThat(output.getEncoding()).isEqualTo("UTF-8");
    }

    @Test
    void shouldReturnBatchConfig() {
        FeedProperties.Batch batch = new FeedProperties.Batch();
        batch.setChunkSize(500);
        batch.setPageSize(500);
        batch.setSkipLimit(5);

        FeedProperties props = new FeedProperties();
        props.setBatch(batch);

        assertThat(props.getBatch().getChunkSize()).isEqualTo(500);
        assertThat(props.getBatch().getPageSize()).isEqualTo(500);
        assertThat(props.getBatch().getSkipLimit()).isEqualTo(5);
    }

    @Test
    void shouldHaveDefaultBatchValues() {
        FeedProperties.Batch batch = new FeedProperties.Batch();
        assertThat(batch.getChunkSize()).isEqualTo(1000);
        assertThat(batch.getPageSize()).isEqualTo(1000);
        assertThat(batch.getSkipLimit()).isEqualTo(10);
    }

    @Test
    void shouldReturnStagingConfig() {
        FeedProperties.DatasourceConfig src = new FeedProperties.DatasourceConfig();
        src.setSelectClause("a, b");
        src.setFromClause("source_table");
        src.setSortKey("a");

        FeedProperties.StagingConfig staging = new FeedProperties.StagingConfig();
        staging.setTableName("my_staging");
        staging.setSource(src);
        staging.setColumns(List.of("a", "b"));

        FeedProperties props = new FeedProperties();
        props.setStaging(staging);

        assertThat(props.getStaging().getTableName()).isEqualTo("my_staging");
        assertThat(props.getStaging().getColumns()).containsExactly("a", "b");
        assertThat(props.getStaging().getSource().getFromClause()).isEqualTo("source_table");
    }

    @Test
    void shouldReturnFeedConfig() {
        FieldDefinition field = new FieldDefinition();
        field.setName("account_no");
        field.setLength(20);

        FeedProperties.FeedOutput feedOutput = new FeedProperties.FeedOutput();
        feedOutput.setFilePrefix("INSTR_");
        feedOutput.setFileSuffix(".dat");

        FeedProperties.DatasourceConfig ds = new FeedProperties.DatasourceConfig();
        ds.setSelectClause("account_no");
        ds.setFromClause("handoff_staging");
        ds.setSortKey("account_no");

        FeedProperties.FeedConfig feed = new FeedProperties.FeedConfig();
        feed.setEnabled(true);
        feed.setOutput(feedOutput);
        feed.setDatasource(ds);
        feed.setFields(List.of(field));

        FeedProperties props = new FeedProperties();
        props.setInstrument(feed);

        assertThat(props.getInstrument().isEnabled()).isTrue();
        assertThat(props.getInstrument().getOutput().getFilePrefix()).isEqualTo("INSTR_");
        assertThat(props.getInstrument().getFields()).hasSize(1);
        assertThat(props.getInstrument().getDatasource().getFromClause()).isEqualTo("handoff_staging");
    }
}
