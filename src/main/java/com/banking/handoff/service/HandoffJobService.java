package com.banking.handoff.service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;

import com.banking.handoff.config.HandoffProperties;
import com.banking.handoff.exception.HandoffException;
import com.banking.handoff.service.dto.HandoffJobRequest;
import com.banking.handoff.service.dto.HandoffJobResponse;
import com.banking.handoff.service.dto.HandoffStatusResponse;

@Service
public class HandoffJobService {

    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final JobLauncher asyncJobLauncher;
    private final Job handoffJob;
    private final JobExplorer jobExplorer;
    private final HandoffProperties properties;

    public HandoffJobService(
            JobLauncher asyncJobLauncher,
            Job handoffJob,
            JobExplorer jobExplorer,
            HandoffProperties properties) {
        this.asyncJobLauncher = asyncJobLauncher;
        this.handoffJob = handoffJob;
        this.jobExplorer = jobExplorer;
        this.properties = properties;
    }

    public HandoffJobResponse generateHandoffFile(HandoffJobRequest request) {
        String outputFileName = resolveOutputFileName(request);
        String outputFilePath = Path.of(
                properties.getOutput().getDirectory(), outputFileName).toString();

        JobParametersBuilder paramsBuilder = new JobParametersBuilder()
                .addString("outputFilePath", outputFilePath)
                .addLong("run.id", System.currentTimeMillis());

        if (request != null && request.getAdditionalParams() != null) {
            request.getAdditionalParams().forEach(paramsBuilder::addString);
        }

        JobParameters jobParameters = paramsBuilder.toJobParameters();

        try {
            JobExecution execution = asyncJobLauncher.run(handoffJob, jobParameters);
            return new HandoffJobResponse(execution.getId(), execution.getStatus().name());
        } catch (Exception e) {
            throw new HandoffException("Failed to launch handoff job: " + e.getMessage(), e);
        }
    }

    public HandoffStatusResponse getJobStatus(Long jobExecutionId) {
        JobExecution execution = jobExplorer.getJobExecution(jobExecutionId);
        if (execution == null) {
            throw new HandoffException("Job execution not found: " + jobExecutionId);
        }

        List<String> failureMessages = execution.getAllFailureExceptions().stream()
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
                failureMessages);
    }

    public boolean isJobRunning(Long jobExecutionId) {
        JobExecution execution = jobExplorer.getJobExecution(jobExecutionId);
        return execution != null && execution.getStatus() == BatchStatus.STARTED;
    }

    private String resolveOutputFileName(HandoffJobRequest request) {
        if (request != null && request.getOutputFileName() != null
                && !request.getOutputFileName().isBlank()) {
            return request.getOutputFileName();
        }
        return properties.getOutput().getFilePrefix()
                + LocalDateTime.now().format(FILE_TIMESTAMP)
                + properties.getOutput().getFileSuffix();
    }
}
