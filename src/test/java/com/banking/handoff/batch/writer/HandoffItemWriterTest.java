package com.banking.handoff.batch.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.core.io.FileSystemResource;

import com.banking.handoff.domain.HandoffRecord;

class HandoffItemWriterTest {

    @TempDir
    File tempDir;

    @Test
    void shouldWriteRecordsToFile() throws Exception {
        File outputFile = new File(tempDir, "test_handoff.dat");

        FlatFileItemWriter<HandoffRecord> flatFileWriter = buildWriter(outputFile);
        flatFileWriter.open(new ExecutionContext());

        HandoffRecord r1 = new HandoffRecord();
        r1.putField("account_no", "ACC001              ");
        r1.putField("name", "John Smith              ");

        HandoffRecord r2 = new HandoffRecord();
        r2.putField("account_no", "ACC002              ");
        r2.putField("name", "Jane Doe                ");

        flatFileWriter.write(new Chunk<>(List.of(r1, r2)));
        flatFileWriter.close();

        List<String> lines = Files.readAllLines(outputFile.toPath());
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).isEqualTo("ACC001              John Smith              ");
        assertThat(lines.get(1)).isEqualTo("ACC002              Jane Doe                ");
    }

    @Test
    void shouldDeleteExistingFileOnRerun() throws Exception {
        File outputFile = new File(tempDir, "rerun_handoff.dat");
        Files.writeString(outputFile.toPath(), "OLD CONTENT\n");

        FlatFileItemWriter<HandoffRecord> flatFileWriter = buildWriter(outputFile);
        flatFileWriter.open(new ExecutionContext());

        HandoffRecord r = new HandoffRecord();
        r.putField("field", "NEWDATA");
        flatFileWriter.write(new Chunk<>(List.of(r)));
        flatFileWriter.close();

        List<String> lines = Files.readAllLines(outputFile.toPath());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).isEqualTo("NEWDATA");
    }

    @Test
    void shouldWriteEmptyFileWhenNoRecords() throws Exception {
        File outputFile = new File(tempDir, "empty_handoff.dat");

        FlatFileItemWriter<HandoffRecord> flatFileWriter = buildWriter(outputFile);
        flatFileWriter.open(new ExecutionContext());
        flatFileWriter.write(new Chunk<>(List.of()));
        flatFileWriter.close();

        assertThat(outputFile).exists();
        assertThat(Files.readAllLines(outputFile.toPath())).isEmpty();
    }

    @Test
    void shouldTrackRecordCountAndDelegateWrite() throws Exception {
        File outputFile = new File(tempDir, "count_handoff.dat");

        FlatFileItemWriter<HandoffRecord> flatFileWriter = buildWriter(outputFile);
        HandoffItemWriter writer = new HandoffItemWriter();
        writer.setDelegate(flatFileWriter);
        writer.open(new ExecutionContext());

        HandoffRecord r1 = new HandoffRecord();
        r1.putField("f", "LINE1");
        HandoffRecord r2 = new HandoffRecord();
        r2.putField("f", "LINE2");
        HandoffRecord r3 = new HandoffRecord();
        r3.putField("f", "LINE3");

        writer.write(new Chunk<>(List.of(r1, r2, r3)));
        writer.close();

        assertThat(writer.getRecordCount()).isEqualTo(3);

        // Verify delegate.write() was actually called — file must contain all 3 lines
        List<String> lines = Files.readAllLines(outputFile.toPath());
        assertThat(lines).containsExactly("LINE1", "LINE2", "LINE3");
    }

    @Test
    void shouldDelegateUpdateToFlatFileWriter() throws Exception {
        File outputFile = new File(tempDir, "update_handoff.dat");
        FlatFileItemWriter<HandoffRecord> flatFileWriter = spy(buildWriter(outputFile));
        HandoffItemWriter writer = new HandoffItemWriter();
        writer.setDelegate(flatFileWriter);
        writer.open(new ExecutionContext());

        ExecutionContext ctx = new ExecutionContext();
        writer.update(ctx);
        writer.close();

        verify(flatFileWriter).update(ctx);
    }

    @Test
    void shouldDelegateCloseToFlatFileWriter() throws Exception {
        File outputFile = new File(tempDir, "close_handoff.dat");
        FlatFileItemWriter<HandoffRecord> flatFileWriter = spy(buildWriter(outputFile));
        HandoffItemWriter writer = new HandoffItemWriter();
        writer.setDelegate(flatFileWriter);
        writer.open(new ExecutionContext());
        writer.close();

        verify(flatFileWriter).close();
    }

    private FlatFileItemWriter<HandoffRecord> buildWriter(File outputFile) {
        return new FlatFileItemWriterBuilder<HandoffRecord>()
                .name("testWriter")
                .resource(new FileSystemResource(outputFile))
                .lineAggregator(item -> String.join("", item.getFields().values()))
                .shouldDeleteIfExists(true)
                .build();
    }
}
