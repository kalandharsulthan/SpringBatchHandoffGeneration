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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.banking.handoff.batch.processor.FeedItemProcessor;
import com.banking.handoff.batch.reader.FeedItemReader;
import com.banking.handoff.batch.reader.FeedQueryConfigRepository;
import com.banking.handoff.batch.writer.FeedItemWriterFactory;
import com.banking.handoff.domain.HandoffRecord;
import com.banking.handoff.util.FixedWidthFormatter;

@Configuration
public class AccountingFeedJobConfig {

    private final FeedQueryConfigRepository feedQueryConfigRepository;
    private final FeedItemWriterFactory feedItemWriterFactory;

    public AccountingFeedJobConfig(
            FeedQueryConfigRepository feedQueryConfigRepository,
            FeedItemWriterFactory feedItemWriterFactory) {
        this.feedQueryConfigRepository = feedQueryConfigRepository;
        this.feedItemWriterFactory = feedItemWriterFactory;
    }

    @Bean
    public Job accountingFeedJob(
            JobRepository jobRepository,
            @Qualifier("accountingFeedStep") Step accountingFeedStep) {
        return new JobBuilder("accountingFeedJob", jobRepository)
                .start(accountingFeedStep)
                .build();
    }

    @Bean("accountingFeedStep")
    public Step accountingFeedStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FeedProperties properties,
            @Qualifier("accountingItemReader") JdbcPagingItemReader<Map<String, Object>> reader,
            @Qualifier("accountingItemProcessor") FeedItemProcessor processor,
            @Qualifier("accountingItemWriter") FlatFileItemWriter<HandoffRecord> writer) {
        return new StepBuilder("accountingFeedStep", jobRepository)
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

    @Bean("accountingItemReader")
    @StepScope
    public JdbcPagingItemReader<Map<String, Object>> accountingItemReader(
            DataSource dataSource,
            FeedProperties properties,
            @Value("#{jobParameters['batchRunId']}") String batchRunId) {
        FeedProperties.DatasourceConfig dsConfig =
                feedQueryConfigRepository.findByFeedName("ACCOUNTING_FEED");
        return new FeedItemReader().reader(
                "accountingItemReader",
                dataSource,
                dsConfig,
                properties.getBatch().getPageSize(),
                Map.of("batchRunId", batchRunId));
    }

    @Bean("accountingItemProcessor")
    public FeedItemProcessor accountingItemProcessor(
            FeedProperties properties, FixedWidthFormatter formatter) {
        return new FeedItemProcessor(properties.getAccounting().getFields(), formatter);
    }

    @Bean("accountingItemWriter")
    @StepScope
    public FlatFileItemWriter<HandoffRecord> accountingItemWriter(
            @Value("#{jobParameters['outputFilePath']}") String outputFilePath,
            FeedProperties properties) {
        return feedItemWriterFactory.create(
                "accountingItemWriter",
                outputFilePath,
                properties.getAccounting().getFormat(),
                properties.getOutput().getEncoding());
    }
}
