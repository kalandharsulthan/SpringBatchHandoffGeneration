package com.banking.handoff.config;

import java.util.Map;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.banking.handoff.batch.reader.FeedItemReader;
import com.banking.handoff.batch.writer.StagingItemWriter;

@Configuration
public class PopulationJobConfig {

    @Bean
    public Job populationJob(
            JobRepository jobRepository,
            @Qualifier("instrumentPopulationStep") Step instrumentPopulationStep,
            @Qualifier("accountingPopulationStep") Step accountingPopulationStep) {
        return new JobBuilder("populationJob", jobRepository)
                .start(instrumentPopulationStep)
                .next(accountingPopulationStep)
                .build();
    }

    @Bean("instrumentPopulationStep")
    public Step instrumentPopulationStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FeedProperties properties,
            @Qualifier("instrumentPopulationReader") JdbcPagingItemReader<Map<String, Object>> reader,
            @Qualifier("instrumentPopulationWriter") JdbcBatchItemWriter<Map<String, Object>> writer) {
        return new StepBuilder("instrumentPopulationStep", jobRepository)
                .<Map<String, Object>, Map<String, Object>>chunk(
                        properties.getBatch().getChunkSize(), transactionManager)
                .reader(reader)
                .writer(writer)
                .faultTolerant()
                .skipLimit(properties.getBatch().getSkipLimit())
                .skip(Exception.class)
                .build();
    }

    @Bean("accountingPopulationStep")
    public Step accountingPopulationStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FeedProperties properties,
            @Qualifier("accountingPopulationReader") JdbcPagingItemReader<Map<String, Object>> reader,
            @Qualifier("accountingPopulationWriter") JdbcBatchItemWriter<Map<String, Object>> writer) {
        return new StepBuilder("accountingPopulationStep", jobRepository)
                .<Map<String, Object>, Map<String, Object>>chunk(
                        properties.getBatch().getChunkSize(), transactionManager)
                .reader(reader)
                .writer(writer)
                .faultTolerant()
                .skipLimit(properties.getBatch().getSkipLimit())
                .skip(Exception.class)
                .build();
    }

    @Bean("instrumentPopulationReader")
    @StepScope
    public JdbcPagingItemReader<Map<String, Object>> instrumentPopulationReader(
            DataSource dataSource,
            FeedProperties properties) {
        return new FeedItemReader().reader(
                "instrumentPopulationReader",
                dataSource,
                properties.getInstrumentStaging().getSource(),
                properties.getBatch().getPageSize(),
                Map.of());
    }

    @Bean("instrumentPopulationWriter")
    @StepScope
    public JdbcBatchItemWriter<Map<String, Object>> instrumentPopulationWriter(
            DataSource dataSource,
            FeedProperties properties,
            @Value("#{jobParameters['batchRunId']}") String batchRunId) {
        FeedProperties.StagingConfig staging = properties.getInstrumentStaging();
        return new StagingItemWriter().writer(
                dataSource, staging.getTableName(), staging.getColumns(), batchRunId);
    }

    @Bean("accountingPopulationReader")
    @StepScope
    public JdbcPagingItemReader<Map<String, Object>> accountingPopulationReader(
            DataSource dataSource,
            FeedProperties properties) {
        return new FeedItemReader().reader(
                "accountingPopulationReader",
                dataSource,
                properties.getAccountingStaging().getSource(),
                properties.getBatch().getPageSize(),
                Map.of());
    }

    @Bean("accountingPopulationWriter")
    @StepScope
    public JdbcBatchItemWriter<Map<String, Object>> accountingPopulationWriter(
            DataSource dataSource,
            FeedProperties properties,
            @Value("#{jobParameters['batchRunId']}") String batchRunId) {
        FeedProperties.StagingConfig staging = properties.getAccountingStaging();
        return new StagingItemWriter().writer(
                dataSource, staging.getTableName(), staging.getColumns(), batchRunId);
    }
}
