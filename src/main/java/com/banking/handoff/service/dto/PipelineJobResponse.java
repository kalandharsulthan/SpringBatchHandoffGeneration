package com.banking.handoff.service.dto;

public class PipelineJobResponse {

    private String runId;
    private String batchRunId;
    private String status;

    public PipelineJobResponse(String runId, String batchRunId, String status) {
        this.runId = runId;
        this.batchRunId = batchRunId;
        this.status = status;
    }

    public String getRunId() { return runId; }
    public String getBatchRunId() { return batchRunId; }
    public String getStatus() { return status; }
}
