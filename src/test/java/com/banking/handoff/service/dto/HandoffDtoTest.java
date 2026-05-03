package com.banking.handoff.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class HandoffDtoTest {

    @Test
    void statusResponseShouldReturnAllFields() {
        HandoffStatusResponse r = new HandoffStatusResponse(
                1L, "COMPLETED", "COMPLETED",
                "2026-05-03T10:00:00", "2026-05-03T10:05:00",
                "/data/handoff/OUT.dat",
                List.of("warn1"));

        assertThat(r.getJobExecutionId()).isEqualTo(1L);
        assertThat(r.getStatus()).isEqualTo("COMPLETED");
        assertThat(r.getExitCode()).isEqualTo("COMPLETED");
        assertThat(r.getStartTime()).isEqualTo("2026-05-03T10:00:00");
        assertThat(r.getEndTime()).isEqualTo("2026-05-03T10:05:00");
        assertThat(r.getOutputFilePath()).isEqualTo("/data/handoff/OUT.dat");
        assertThat(r.getFailureMessages()).containsExactly("warn1");
    }

    @Test
    void statusResponseShouldReturnNonEmptyFailureMessages() {
        HandoffStatusResponse r = new HandoffStatusResponse(
                2L, "FAILED", "FAILED", null, null, null,
                List.of("err1", "err2"));

        assertThat(r.getFailureMessages()).hasSize(2);
        assertThat(r.getFailureMessages()).containsExactly("err1", "err2");
        assertThat(r.getStartTime()).isNull();
        assertThat(r.getEndTime()).isNull();
    }

    @Test
    void jobRequestShouldReturnAdditionalParams() {
        HandoffJobRequest req = new HandoffJobRequest();
        req.setOutputFileName("OUT.dat");
        req.setAdditionalParams(Map.of("key1", "val1"));

        assertThat(req.getOutputFileName()).isEqualTo("OUT.dat");
        assertThat(req.getAdditionalParams()).containsEntry("key1", "val1");
    }

    @Test
    void jobResponseShouldReturnAllFields() {
        HandoffJobResponse resp = new HandoffJobResponse(42L, "STARTING");
        assertThat(resp.getJobExecutionId()).isEqualTo(42L);
        assertThat(resp.getStatus()).isEqualTo("STARTING");
    }
}
