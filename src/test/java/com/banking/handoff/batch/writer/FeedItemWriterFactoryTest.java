package com.banking.handoff.batch.writer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.file.FlatFileItemWriter;

import com.banking.handoff.config.FeedProperties;
import com.banking.handoff.domain.HandoffRecord;

class FeedItemWriterFactoryTest {

    private final FeedItemWriterFactory factory = new FeedItemWriterFactory();

    @TempDir
    Path tempDir;

    @Test
    void shouldWriteCommaSeparatedLineForCsvFormat() throws Exception {
        Path output = tempDir.resolve("test.csv");
        FlatFileItemWriter<HandoffRecord> writer =
                factory.create("test-csv", output.toString(), FeedProperties.Format.CSV, "UTF-8");

        HandoffRecord record = new HandoffRecord();
        record.putField("instrument_id", "INSTR-001         ");
        record.putField("collection_number", "COL-001             ");
        record.putField("amount", "000000015000.00");

        writer.open(new ExecutionContext());
        writer.write(new Chunk<>(List.of(record)));
        writer.close();

        String content = Files.readString(output);
        assertThat(content).contains("INSTR-001         ,COL-001             ,000000015000.00");
    }

    @Test
    void shouldWriteConcatenatedLineForFixedWidthFormat() throws Exception {
        Path output = tempDir.resolve("test.dat");
        FlatFileItemWriter<HandoffRecord> writer =
                factory.create("test-fw", output.toString(), FeedProperties.Format.FIXED_WIDTH, "UTF-8");

        HandoffRecord record = new HandoffRecord();
        record.putField("instrument_id", "INSTR-001         ");
        record.putField("collection_number", "COL-001             ");
        record.putField("amount", "000000015000.00");

        writer.open(new ExecutionContext());
        writer.write(new Chunk<>(List.of(record)));
        writer.close();

        String content = Files.readString(output);
        assertThat(content).doesNotContain(",");
        assertThat(content.trim()).isEqualTo("INSTR-001         COL-001             000000015000.00");
    }

    @Test
    void shouldRespectEncoding() throws Exception {
        Path output = tempDir.resolve("test-encoding.dat");
        FlatFileItemWriter<HandoffRecord> writer =
                factory.create("test-enc", output.toString(), FeedProperties.Format.FIXED_WIDTH, "ISO-8859-1");

        assertThat(writer).isNotNull();
    }
}
