package com.banking.handoff.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.banking.handoff.domain.FieldDefinition;

class HandoffPropertiesTest {

    @Test
    void shouldReturnOutputConfig() {
        HandoffProperties.Output output = new HandoffProperties.Output();
        output.setDirectory("/tmp/handoff");
        output.setFilePrefix("HDR_");
        output.setFileSuffix(".txt");
        output.setEncoding("ISO-8859-1");

        HandoffProperties props = new HandoffProperties();
        props.setOutput(output);

        assertThat(props.getOutput()).isSameAs(output);
        assertThat(props.getOutput().getDirectory()).isEqualTo("/tmp/handoff");
        assertThat(props.getOutput().getFilePrefix()).isEqualTo("HDR_");
        assertThat(props.getOutput().getFileSuffix()).isEqualTo(".txt");
        assertThat(props.getOutput().getEncoding()).isEqualTo("ISO-8859-1");
    }

    @Test
    void shouldReturnBatchConfig() {
        HandoffProperties.Batch batch = new HandoffProperties.Batch();
        batch.setChunkSize(500);
        batch.setPageSize(500);
        batch.setSkipLimit(5);

        HandoffProperties props = new HandoffProperties();
        props.setBatch(batch);

        assertThat(props.getBatch()).isSameAs(batch);
        assertThat(props.getBatch().getChunkSize()).isEqualTo(500);
        assertThat(props.getBatch().getPageSize()).isEqualTo(500);
        assertThat(props.getBatch().getSkipLimit()).isEqualTo(5);
    }

    @Test
    void shouldReturnDatasourceConfig() {
        HandoffProperties.Datasource ds = new HandoffProperties.Datasource();
        ds.setSelectClause("a, b");
        ds.setFromClause("my_table");
        ds.setWhereClause("active = true");
        ds.setSortKey("a");

        HandoffProperties props = new HandoffProperties();
        props.setDatasource(ds);

        assertThat(props.getDatasource()).isSameAs(ds);
        assertThat(props.getDatasource().getSelectClause()).isEqualTo("a, b");
        assertThat(props.getDatasource().getFromClause()).isEqualTo("my_table");
        assertThat(props.getDatasource().getWhereClause()).isEqualTo("active = true");
        assertThat(props.getDatasource().getSortKey()).isEqualTo("a");
    }

    @Test
    void shouldReturnFieldsList() {
        FieldDefinition f = new FieldDefinition();
        f.setName("col1");
        f.setLength(10);

        HandoffProperties props = new HandoffProperties();
        props.setFields(List.of(f));

        assertThat(props.getFields()).hasSize(1);
        assertThat(props.getFields().get(0).getName()).isEqualTo("col1");
    }

    @Test
    void shouldHaveDefaultOutputValues() {
        HandoffProperties.Output output = new HandoffProperties.Output();
        assertThat(output.getFilePrefix()).isEqualTo("HANDOFF_");
        assertThat(output.getFileSuffix()).isEqualTo(".dat");
        assertThat(output.getEncoding()).isEqualTo("UTF-8");
    }

    @Test
    void shouldHaveDefaultBatchValues() {
        HandoffProperties.Batch batch = new HandoffProperties.Batch();
        assertThat(batch.getChunkSize()).isEqualTo(1000);
        assertThat(batch.getPageSize()).isEqualTo(1000);
        assertThat(batch.getSkipLimit()).isEqualTo(10);
    }
}
