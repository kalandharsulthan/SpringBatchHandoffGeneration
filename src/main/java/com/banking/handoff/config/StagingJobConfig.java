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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.banking.handoff.batch.reader.FeedItemReader;
import com.banking.handoff.batch.writer.StagingItemWriter;

@Configuration
public class StagingJobConfig {

    @Bean
    public Job stagingJob(JobRepository jobRepository, Step stagingStep) {
        return new JobBuilder("stagingJob", jobRepository)
                .start(stagingStep)
                .build();
    }

    @Bean
    public Step stagingStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FeedProperties properties,
            JdbcPagingItemReader<Map<String, Object>> stagingItemReader,
            JdbcBatchItemWriter<Map<String, Object>> stagingItemWriter) {

        return new StepBuilder("stagingStep", jobRepository)
                .<Map<String, Object>, Map<String, Object>>chunk(
                        properties.getBatch().getChunkSize(), transactionManager)
                .reader(stagingItemReader)
                .writer(stagingItemWriter)
                .faultTolerant()
                .skipLimit(properties.getBatch().getSkipLimit())
                .skip(Exception.class)
                .build();
    }

    @Bean
    @StepScope
    public JdbcPagingItemReader<Map<String, Object>> stagingItemReader(
            DataSource dataSource,
            FeedProperties properties) {
        return new FeedItemReader().reader(
                "stagingItemReader",
                dataSource,
                properties.getStaging().getSource(),
                properties.getBatch().getPageSize(),
                Map.of());
    }

    @Bean
    @StepScope
    public JdbcBatchItemWriter<Map<String, Object>> stagingItemWriter(
            DataSource dataSource,
            FeedProperties properties,
            @Value("#{jobParameters['batchRunId']}") String batchRunId) {
        FeedProperties.StagingConfig staging = properties.getStaging();
        return new StagingItemWriter().writer(
                dataSource, staging.getTableName(), staging.getColumns(), batchRunId);
    }
}
