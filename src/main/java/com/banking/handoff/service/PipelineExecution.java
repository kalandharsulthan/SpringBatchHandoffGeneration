package com.banking.handoff.service;

public class PipelineExecution {

    private final String runId;
    private final String batchRunId;
    private volatile String status = "RUNNING";
    private volatile String errorMessage;

    private volatile Long stagingJobExecutionId;
    private volatile String stagingStatus;

    private volatile Long instrumentJobExecutionId;
    private volatile String instrumentStatus;
    private volatile String instrumentOutputFilePath;

    private volatile Long accountingJobExecutionId;
    private volatile String accountingStatus;
    private volatile String accountingOutputFilePath;

    public PipelineExecution(String runId, String batchRunId) {
        this.runId = runId;
        this.batchRunId = batchRunId;
    }

    public String getRunId() { return runId; }
    public String getBatchRunId() { return batchRunId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Long getStagingJobExecutionId() { return stagingJobExecutionId; }
    public void setStagingJobExecutionId(Long id) { this.stagingJobExecutionId = id; }

    public String getStagingStatus() { return stagingStatus; }
    public void setStagingStatus(String stagingStatus) { this.stagingStatus = stagingStatus; }

    public Long getInstrumentJobExecutionId() { return instrumentJobExecutionId; }
    public void setInstrumentJobExecutionId(Long id) { this.instrumentJobExecutionId = id; }

    public String getInstrumentStatus() { return instrumentStatus; }
    public void setInstrumentStatus(String instrumentStatus) { this.instrumentStatus = instrumentStatus; }

    public String getInstrumentOutputFilePath() { return instrumentOutputFilePath; }
    public void setInstrumentOutputFilePath(String p) { this.instrumentOutputFilePath = p; }

    public Long getAccountingJobExecutionId() { return accountingJobExecutionId; }
    public void setAccountingJobExecutionId(Long id) { this.accountingJobExecutionId = id; }

    public String getAccountingStatus() { return accountingStatus; }
    public void setAccountingStatus(String accountingStatus) { this.accountingStatus = accountingStatus; }

    public String getAccountingOutputFilePath() { return accountingOutputFilePath; }
    public void setAccountingOutputFilePath(String p) { this.accountingOutputFilePath = p; }
}
