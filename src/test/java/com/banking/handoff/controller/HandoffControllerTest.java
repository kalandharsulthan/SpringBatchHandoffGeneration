package com.banking.handoff.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.banking.handoff.exception.HandoffException;
import com.banking.handoff.service.PipelineJobService;
import com.banking.handoff.service.dto.HandoffStatusResponse;
import com.banking.handoff.service.dto.PipelineJobResponse;
import com.banking.handoff.service.dto.PipelineStatusResponse;

@WebMvcTest(HandoffController.class)
class HandoffControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PipelineJobService pipelineJobService;

    @Test
    void shouldReturn202WhenPipelineLaunched() throws Exception {
        when(pipelineJobService.startPipeline())
                .thenReturn(new PipelineJobResponse("run-uuid", "batch-uuid", "ACCEPTED"));

        mockMvc.perform(post("/api/handoff/pipeline")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.runId").value("run-uuid"))
                .andExpect(jsonPath("$.batchRunId").value("batch-uuid"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void shouldReturn202WhenPipelineLaunchedWithBody() throws Exception {
        when(pipelineJobService.startPipeline())
                .thenReturn(new PipelineJobResponse("run-uuid", "batch-uuid", "ACCEPTED"));

        mockMvc.perform(post("/api/handoff/pipeline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.runId").isNotEmpty());
    }

    @Test
    void shouldReturn200WithPipelineStatus() throws Exception {
        PipelineStatusResponse statusResponse = new PipelineStatusResponse(
                "run-uuid", "batch-uuid", "COMPLETED", null,
                1L, "COMPLETED",
                2L, "COMPLETED", "/data/INSTRUMENT_20260507.dat",
                3L, "COMPLETED", "/data/ACCOUNTING_20260507.dat");

        when(pipelineJobService.getPipelineStatus("run-uuid")).thenReturn(statusResponse);

        mockMvc.perform(get("/api/handoff/pipeline/run-uuid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("run-uuid"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.stagingJobExecutionId").value(1))
                .andExpect(jsonPath("$.instrumentJobExecutionId").value(2))
                .andExpect(jsonPath("$.accountingJobExecutionId").value(3));
    }

    @Test
    void shouldReturn200WithIndividualJobStatus() throws Exception {
        HandoffStatusResponse jobStatus = new HandoffStatusResponse(
                42L, "COMPLETED", "COMPLETED",
                "2026-05-07T10:00:00", "2026-05-07T10:05:00",
                "/data/INSTRUMENT_20260507.dat",
                List.of());

        when(pipelineJobService.getJobStatus(42L)).thenReturn(jobStatus);

        mockMvc.perform(get("/api/handoff/status/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobExecutionId").value(42))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void shouldReturn400WhenPipelineRunNotFound() throws Exception {
        when(pipelineJobService.getPipelineStatus("unknown"))
                .thenThrow(new HandoffException("Pipeline execution not found: unknown"));

        mockMvc.perform(get("/api/handoff/pipeline/unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Pipeline execution not found: unknown"));
    }
}
