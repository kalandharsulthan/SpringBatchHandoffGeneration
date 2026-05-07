package com.banking.handoff.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.banking.handoff.exception.HandoffException;
import com.banking.handoff.service.PipelineJobService;
import com.banking.handoff.service.dto.HandoffStatusResponse;
import com.banking.handoff.service.dto.PipelineJobRequest;
import com.banking.handoff.service.dto.PipelineJobResponse;
import com.banking.handoff.service.dto.PipelineStatusResponse;

import java.util.Map;

@RestController
@RequestMapping("/api/handoff")
public class HandoffController {

    private final PipelineJobService pipelineJobService;

    public HandoffController(PipelineJobService pipelineJobService) {
        this.pipelineJobService = pipelineJobService;
    }

    /**
     * Triggers the full pipeline: staging → instrument feed → accounting feed.
     * Returns immediately; poll /pipeline/{runId} for status.
     */
    @PostMapping("/pipeline")
    public ResponseEntity<PipelineJobResponse> runPipeline(
            @RequestBody(required = false) PipelineJobRequest request) {
        PipelineJobResponse response = pipelineJobService.startPipeline();
        return ResponseEntity.accepted().body(response);
    }

    /**
     * Returns aggregated pipeline status across all three jobs.
     */
    @GetMapping("/pipeline/{runId}")
    public ResponseEntity<PipelineStatusResponse> pipelineStatus(
            @PathVariable String runId) {
        PipelineStatusResponse response = pipelineJobService.getPipelineStatus(runId);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns status of an individual Spring Batch job execution by ID.
     */
    @GetMapping("/status/{jobExecutionId}")
    public ResponseEntity<HandoffStatusResponse> jobStatus(
            @PathVariable Long jobExecutionId) {
        HandoffStatusResponse response = pipelineJobService.getJobStatus(jobExecutionId);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(HandoffException.class)
    public ResponseEntity<Map<String, String>> handleHandoffException(HandoffException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
