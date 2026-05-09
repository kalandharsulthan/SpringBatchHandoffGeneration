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
    void shouldReturnInstrumentStagingConfig() {
        FeedProperties.DatasourceConfig src = new FeedProperties.DatasourceConfig();
        src.setSelectClause("instrument_id, collection_number");
        src.setFromClause("instrument_header");
        src.setSortKey("instrument_id");

        FeedProperties.StagingConfig staging = new FeedProperties.StagingConfig();
        staging.setTableName("instrument_header_staging");
        staging.setSource(src);
        staging.setColumns(List.of("instrument_id", "collection_number"));

        FeedProperties props = new FeedProperties();
        props.setInstrumentStaging(staging);

        assertThat(props.getInstrumentStaging().getTableName()).isEqualTo("instrument_header_staging");
        assertThat(props.getInstrumentStaging().getColumns()).containsExactly("instrument_id", "collection_number");
        assertThat(props.getInstrumentStaging().getSource().getFromClause()).isEqualTo("instrument_header");
    }

    @Test
    void shouldReturnFeedConfig() {
        FieldDefinition field = new FieldDefinition();
        field.setName("instrument_id");
        field.setLength(20);

        FeedProperties.FeedOutput feedOutput = new FeedProperties.FeedOutput();
        feedOutput.setFilePrefix("INSTRUMENT_FEED_");
        feedOutput.setFileSuffix(".dat");

        FeedProperties.FeedConfig feed = new FeedProperties.FeedConfig();
        feed.setEnabled(true);
        feed.setFormat(FeedProperties.Format.CSV);
        feed.setOutput(feedOutput);
        feed.setFields(List.of(field));

        FeedProperties props = new FeedProperties();
        props.setInstrument(feed);

        assertThat(props.getInstrument().isEnabled()).isTrue();
        assertThat(props.getInstrument().getFormat()).isEqualTo(FeedProperties.Format.CSV);
        assertThat(props.getInstrument().getOutput().getFilePrefix()).isEqualTo("INSTRUMENT_FEED_");
        assertThat(props.getInstrument().getFields()).hasSize(1);
    }
}
