package com.banking.handoff.service.dto;

import java.util.Map;

public class PipelineJobRequest {

    private Map<String, String> additionalParams;

    public Map<String, String> getAdditionalParams() {
        return additionalParams;
    }

    public void setAdditionalParams(Map<String, String> additionalParams) {
        this.additionalParams = additionalParams;
    }
}
