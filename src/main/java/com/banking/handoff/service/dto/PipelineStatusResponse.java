package com.banking.handoff.service.dto;

public class PipelineStatusResponse {

    private String runId;
    private String batchRunId;
    private String status;
    private String errorMessage;

    private Long stagingJobExecutionId;
    private String stagingStatus;

    private Long instrumentJobExecutionId;
    private String instrumentStatus;
    private String instrumentOutputFilePath;

    private Long accountingJobExecutionId;
    private String accountingStatus;
    private String accountingOutputFilePath;

    public PipelineStatusResponse(
            String runId,
            String batchRunId,
            String status,
            String errorMessage,
            Long stagingJobExecutionId,
            String stagingStatus,
            Long instrumentJobExecutionId,
            String instrumentStatus,
            String instrumentOutputFilePath,
            Long accountingJobExecutionId,
            String accountingStatus,
            String accountingOutputFilePath) {
        this.runId = runId;
        this.batchRunId = batchRunId;
        this.status = status;
        this.errorMessage = errorMessage;
        this.stagingJobExecutionId = stagingJobExecutionId;
        this.stagingStatus = stagingStatus;
        this.instrumentJobExecutionId = instrumentJobExecutionId;
        this.instrumentStatus = instrumentStatus;
        this.instrumentOutputFilePath = instrumentOutputFilePath;
        this.accountingJobExecutionId = accountingJobExecutionId;
        this.accountingStatus = accountingStatus;
        this.accountingOutputFilePath = accountingOutputFilePath;
    }

    public String getRunId() { return runId; }
    public String getBatchRunId() { return batchRunId; }
    public String getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public Long getStagingJobExecutionId() { return stagingJobExecutionId; }
    public String getStagingStatus() { return stagingStatus; }
    public Long getInstrumentJobExecutionId() { return instrumentJobExecutionId; }
    public String getInstrumentStatus() { return instrumentStatus; }
    public String getInstrumentOutputFilePath() { return instrumentOutputFilePath; }
    public Long getAccountingJobExecutionId() { return accountingJobExecutionId; }
    public String getAccountingStatus() { return accountingStatus; }
    public String getAccountingOutputFilePath() { return accountingOutputFilePath; }
}
