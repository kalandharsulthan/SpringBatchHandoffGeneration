package com.banking.handoff.config;

import java.util.Map;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import com.banking.handoff.batch.processor.FeedItemProcessor;
import com.banking.handoff.batch.reader.FeedItemReader;
import com.banking.handoff.domain.HandoffRecord;
import com.banking.handoff.util.FixedWidthFormatter;

@Configuration
public class InstrumentFeedJobConfig {

    @Bean
    public Job instrumentFeedJob(
            JobRepository jobRepository,
            @Qualifier("instrumentFeedStep") Step instrumentFeedStep) {
        return new JobBuilder("instrumentFeedJob", jobRepository)
                .start(instrumentFeedStep)
                .build();
    }

    @Bean("instrumentFeedStep")
    public Step instrumentFeedStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FeedProperties properties,
            @Qualifier("instrumentItemReader") JdbcPagingItemReader<Map<String, Object>> reader,
            @Qualifier("instrumentItemProcessor") FeedItemProcessor processor,
            @Qualifier("instrumentItemWriter") FlatFileItemWriter<HandoffRecord> writer) {

        return new StepBuilder("instrumentFeedStep", jobRepository)
                .<Map<String, Object>, HandoffRecord>chunk(
                        properties.getBatch().getChunkSize(), transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .skipLimit(properties.getBatch().getSkipLimit())
                .skip(Exception.class)
                .build();
    }

    @Bean("instrumentItemReader")
    @StepScope
    public JdbcPagingItemReader<Map<String, Object>> instrumentItemReader(
            DataSource dataSource,
            FeedProperties properties,
            @Value("#{jobParameters['batchRunId']}") String batchRunId) {
        return new FeedItemReader().reader(
                "instrumentItemReader",
                dataSource,
                properties.getInstrument().getDatasource(),
                properties.getBatch().getPageSize(),
                Map.of("batchRunId", batchRunId));
    }

    @Bean("instrumentItemProcessor")
    public FeedItemProcessor instrumentItemProcessor(
            FeedProperties properties, FixedWidthFormatter formatter) {
        return new FeedItemProcessor(properties.getInstrument().getFields(), formatter);
    }

    @Bean("instrumentItemWriter")
    @StepScope
    public FlatFileItemWriter<HandoffRecord> instrumentItemWriter(
            @Value("#{jobParameters['outputFilePath']}") String outputFilePath,
            FeedProperties properties) {
        return new FlatFileItemWriterBuilder<HandoffRecord>()
                .name("instrumentItemWriter")
                .resource(new FileSystemResource(outputFilePath))
                .lineAggregator(item -> String.join("", item.getFields().values()))
                .encoding(properties.getOutput().getEncoding())
                .shouldDeleteIfExists(true)
                .build();
    }
}
