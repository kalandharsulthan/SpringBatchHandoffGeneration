package com.banking.handoff.batch.writer;

import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import com.banking.handoff.config.FeedProperties;
import com.banking.handoff.domain.HandoffRecord;

@Component
public class FeedItemWriterFactory {

    public FlatFileItemWriter<HandoffRecord> create(
            String name,
            String outputFilePath,
            FeedProperties.Format format,
            String encoding) {

        var builder = new FlatFileItemWriterBuilder<HandoffRecord>()
                .name(name)
                .resource(new FileSystemResource(outputFilePath))
                .encoding(encoding)
                .shouldDeleteIfExists(true);

        if (format == FeedProperties.Format.CSV) {
            builder.lineAggregator(item -> String.join(",", item.getFields().values()));
        } else {
            builder.lineAggregator(item -> String.join("", item.getFields().values()));
        }

        return builder.build();
    }
}
