package com.banking.handoff.service.dto;

import java.util.Map;

public class HandoffJobRequest {

    private String outputFileName;
    private Map<String, String> additionalParams;

    public String getOutputFileName() {
        return outputFileName;
    }

    public void setOutputFileName(String outputFileName) {
        this.outputFileName = outputFileName;
    }

    public Map<String, String> getAdditionalParams() {
        return additionalParams;
    }

    public void setAdditionalParams(Map<String, String> additionalParams) {
        this.additionalParams = additionalParams;
    }
}
