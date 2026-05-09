package com.banking.handoff.service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.banking.handoff.config.FeedProperties;
import com.banking.handoff.exception.HandoffException;
import com.banking.handoff.service.dto.HandoffStatusResponse;
import com.banking.handoff.service.dto.PipelineJobResponse;
import com.banking.handoff.service.dto.PipelineStatusResponse;

@Service
public class PipelineJobService {

    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final JobLauncher syncJobLauncher;
    private final Job populationJob;
    private final Job instrumentFeedJob;
    private final Job accountingFeedJob;
    private final JobExplorer jobExplorer;
    private final FeedProperties properties;

    private final ConcurrentMap<String, PipelineExecution> executions = new ConcurrentHashMap<>();

    public PipelineJobService(
            @Qualifier("syncJobLauncher") JobLauncher syncJobLauncher,
            @Qualifier("populationJob") Job populationJob,
            @Qualifier("instrumentFeedJob") Job instrumentFeedJob,
            @Qualifier("accountingFeedJob") Job accountingFeedJob,
            JobExplorer jobExplorer,
            FeedProperties properties) {
        this.syncJobLauncher = syncJobLauncher;
        this.populationJob = populationJob;
        this.instrumentFeedJob = instrumentFeedJob;
        this.accountingFeedJob = accountingFeedJob;
        this.jobExplorer = jobExplorer;
        this.properties = properties;
    }

    public PipelineJobResponse startPipeline() {
        String runId = UUID.randomUUID().toString();
        String batchRunId = UUID.randomUUID().toString();
        PipelineExecution pe = new PipelineExecution(runId, batchRunId);
        executions.put(runId, pe);
        runPipelineAsync(pe);
        return new PipelineJobResponse(runId, batchRunId, "ACCEPTED");
    }

    @Async
    public void runPipelineAsync(PipelineExecution pe) {
        try {
            runPopulation(pe);

            if (!"COMPLETED".equals(pe.getStagingStatus())) {
                pe.setStatus("FAILED_STAGING");
                return;
            }

            if (properties.getInstrument().isEnabled()) {
                runInstrumentFeed(pe);
                if (!"COMPLETED".equals(pe.getInstrumentStatus())) {
                    pe.setStatus("FAILED_INSTRUMENT_FEED");
                    return;
                }
            }

            if (properties.getAccounting().isEnabled()) {
                runAccountingFeed(pe);
                if (!"COMPLETED".equals(pe.getAccountingStatus())) {
                    pe.setStatus("FAILED_ACCOUNTING_FEED");
                    return;
                }
            }

            pe.setStatus("COMPLETED");

        } catch (Exception e) {
            pe.setStatus("FAILED");
            pe.setErrorMessage(e.getMessage());
        }
    }

    private void runPopulation(PipelineExecution pe) throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("batchRunId", pe.getBatchRunId())
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        JobExecution exec = syncJobLauncher.run(populationJob, params);
        pe.setStagingJobExecutionId(exec.getId());
        pe.setStagingStatus(exec.getStatus().name());
    }

    private void runInstrumentFeed(PipelineExecution pe) throws Exception {
        String outputFilePath = resolveFilePath(
                properties.getInstrument().getOutput(), pe.getBatchRunId());
        pe.setInstrumentOutputFilePath(outputFilePath);

        JobParameters params = new JobParametersBuilder()
                .addString("batchRunId", pe.getBatchRunId())
                .addString("outputFilePath", outputFilePath)
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        JobExecution exec = syncJobLauncher.run(instrumentFeedJob, params);
        pe.setInstrumentJobExecutionId(exec.getId());
        pe.setInstrumentStatus(exec.getStatus().name());
    }

    private void runAccountingFeed(PipelineExecution pe) throws Exception {
        String outputFilePath = resolveFilePath(
                properties.getAccounting().getOutput(), pe.getBatchRunId());
        pe.setAccountingOutputFilePath(outputFilePath);

        JobParameters params = new JobParametersBuilder()
                .addString("batchRunId", pe.getBatchRunId())
                .addString("outputFilePath", outputFilePath)
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        JobExecution exec = syncJobLauncher.run(accountingFeedJob, params);
        pe.setAccountingJobExecutionId(exec.getId());
        pe.setAccountingStatus(exec.getStatus().name());
    }

    private String resolveFilePath(FeedProperties.FeedOutput feedOutput, String batchRunId) {
        String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP);
        String shortId = batchRunId.substring(0, Math.min(8, batchRunId.length()));
        String fileName = feedOutput.getFilePrefix() + timestamp + "_" + shortId
                + feedOutput.getFileSuffix();
        return Path.of(properties.getOutput().getDirectory(), fileName).toString();
    }

    public PipelineStatusResponse getPipelineStatus(String runId) {
        PipelineExecution pe = executions.get(runId);
        if (pe == null) {
            throw new HandoffException("Pipeline execution not found: " + runId);
        }
        return new PipelineStatusResponse(
                pe.getRunId(),
                pe.getBatchRunId(),
                pe.getStatus(),
                pe.getErrorMessage(),
                pe.getStagingJobExecutionId(),
                pe.getStagingStatus(),
                pe.getInstrumentJobExecutionId(),
                pe.getInstrumentStatus(),
                pe.getInstrumentOutputFilePath(),
                pe.getAccountingJobExecutionId(),
                pe.getAccountingStatus(),
                pe.getAccountingOutputFilePath());
    }

    public HandoffStatusResponse getJobStatus(Long jobExecutionId) {
        JobExecution execution = jobExplorer.getJobExecution(jobExecutionId);
        if (execution == null) {
            throw new HandoffException("Job execution not found: " + jobExecutionId);
        }
        List<String> failures = execution.getAllFailureExceptions().stream()
                .map(Throwable::getMessage)
                .toList();
        String outputFilePath = execution.getJobParameters().getString("outputFilePath");
        return new HandoffStatusResponse(
                execution.getId(),
                execution.getStatus().name(),
                execution.getExitStatus().getExitCode(),
                execution.getStartTime() != null ? execution.getStartTime().toString() : null,
                execution.getEndTime() != null ? execution.getEndTime().toString() : null,
                outputFilePath,
                failures);
    }

    public boolean isPipelineRunning(String runId) {
        PipelineExecution pe = executions.get(runId);
        return pe != null && "RUNNING".equals(pe.getStatus());
    }
}
