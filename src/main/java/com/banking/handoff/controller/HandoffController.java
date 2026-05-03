package com.banking.handoff.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.banking.handoff.exception.HandoffException;
import com.banking.handoff.service.HandoffJobService;
import com.banking.handoff.service.dto.HandoffJobRequest;
import com.banking.handoff.service.dto.HandoffJobResponse;
import com.banking.handoff.service.dto.HandoffStatusResponse;

import java.util.Map;

@RestController
@RequestMapping("/api/handoff")
public class HandoffController {

    private final HandoffJobService handoffJobService;

    public HandoffController(HandoffJobService handoffJobService) {
        this.handoffJobService = handoffJobService;
    }

    @PostMapping("/generate")
    public ResponseEntity<HandoffJobResponse> generate(
            @RequestBody(required = false) HandoffJobRequest request) {
        HandoffJobResponse response = handoffJobService.generateHandoffFile(request);
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/status/{jobExecutionId}")
    public ResponseEntity<HandoffStatusResponse> status(
            @PathVariable Long jobExecutionId) {
        HandoffStatusResponse response = handoffJobService.getJobStatus(jobExecutionId);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(HandoffException.class)
    public ResponseEntity<Map<String, String>> handleHandoffException(HandoffException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage()));
    }
}
