package com.banking.handoff.service.dto;

public class HandoffJobResponse {

    private Long jobExecutionId;
    private String status;

    public HandoffJobResponse(Long jobExecutionId, String status) {
        this.jobExecutionId = jobExecutionId;
        this.status = status;
    }

    public Long getJobExecutionId() {
        return jobExecutionId;
    }

    public String getStatus() {
        return status;
    }
}
