package com.banking.handoff.config;

import java.util.Map;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import com.banking.handoff.batch.processor.HandoffItemProcessor;
import com.banking.handoff.batch.reader.HandoffItemReader;
import com.banking.handoff.domain.HandoffRecord;

@Configuration
public class BatchJobConfig {

    @Bean
    public TaskExecutorJobLauncher asyncJobLauncher(JobRepository jobRepository) throws Exception {
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(new SimpleAsyncTaskExecutor());
        launcher.afterPropertiesSet();
        return launcher;
    }

    @Bean
    public Job handoffJob(JobRepository jobRepository, Step handoffStep) {
        return new JobBuilder("handoffJob", jobRepository)
                .start(handoffStep)
                .build();
    }

    @Bean
    public Step handoffStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            HandoffProperties properties,
            JdbcPagingItemReader<Map<String, Object>> handoffItemReader,
            HandoffItemProcessor itemProcessor,
            FlatFileItemWriter<HandoffRecord> handoffItemWriter) {

        return new StepBuilder("handoffStep", jobRepository)
                .<Map<String, Object>, HandoffRecord>chunk(
                        properties.getBatch().getChunkSize(), transactionManager)
                .reader(handoffItemReader)
                .processor(itemProcessor)
                .writer(handoffItemWriter)
                .faultTolerant()
                .skipLimit(properties.getBatch().getSkipLimit())
                .skip(Exception.class)
                .build();
    }

    @Bean
    @StepScope
    public JdbcPagingItemReader<Map<String, Object>> handoffItemReader(
            DataSource dataSource,
            HandoffProperties properties) {
        return new HandoffItemReader().reader(dataSource, properties);
    }

    @Bean
    @StepScope
    public FlatFileItemWriter<HandoffRecord> handoffItemWriter(
            @Value("#{jobParameters['outputFilePath']}") String outputFilePath,
            HandoffProperties properties) {

        return new FlatFileItemWriterBuilder<HandoffRecord>()
                .name("handoffItemWriter")
                .resource(new FileSystemResource(
                        outputFilePath != null ? outputFilePath : "/tmp/handoff_default.dat"))
                .lineAggregator(item -> String.join("", item.getFields().values()))
                .encoding(properties.getOutput().getEncoding())
                .shouldDeleteIfExists(true)
                .build();
    }
}
