package com.banking.handoff.service.dto;

import java.util.List;

public class HandoffStatusResponse {

    private Long jobExecutionId;
    private String status;
    private String exitCode;
    private String startTime;
    private String endTime;
    private String outputFilePath;
    private List<String> failureMessages;

    public HandoffStatusResponse(
            Long jobExecutionId,
            String status,
            String exitCode,
            String startTime,
            String endTime,
            String outputFilePath,
            List<String> failureMessages) {
        this.jobExecutionId = jobExecutionId;
        this.status = status;
        this.exitCode = exitCode;
        this.startTime = startTime;
        this.endTime = endTime;
        this.outputFilePath = outputFilePath;
        this.failureMessages = failureMessages;
    }

    public Long getJobExecutionId() {
        return jobExecutionId;
    }

    public String getStatus() {
        return status;
    }

    public String getExitCode() {
        return exitCode;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public String getOutputFilePath() {
        return outputFilePath;
    }

    public List<String> getFailureMessages() {
        return failureMessages;
    }
}
