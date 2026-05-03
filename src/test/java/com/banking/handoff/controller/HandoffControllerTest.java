package com.banking.handoff.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
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
import com.banking.handoff.service.HandoffJobService;
import com.banking.handoff.service.dto.HandoffJobResponse;
import com.banking.handoff.service.dto.HandoffStatusResponse;

@WebMvcTest(HandoffController.class)
class HandoffControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HandoffJobService handoffJobService;

    @Test
    void shouldReturn202WhenJobLaunchedWithoutBody() throws Exception {
        when(handoffJobService.generateHandoffFile(isNull()))
                .thenReturn(new HandoffJobResponse(1L, "STARTING"));

        mockMvc.perform(post("/api/handoff/generate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobExecutionId").value(1))
                .andExpect(jsonPath("$.status").value("STARTING"));
    }

    @Test
    void shouldReturn202WhenJobLaunchedWithBody() throws Exception {
        when(handoffJobService.generateHandoffFile(any()))
                .thenReturn(new HandoffJobResponse(5L, "STARTING"));

        mockMvc.perform(post("/api/handoff/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "outputFileName": "TEST_FILE.dat"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobExecutionId").value(5));
    }

    @Test
    void shouldReturn200WithStatusForKnownJobExecution() throws Exception {
        HandoffStatusResponse statusResponse = new HandoffStatusResponse(
                42L, "COMPLETED", "COMPLETED",
                "2026-05-03T10:00:00", "2026-05-03T10:05:00",
                "/data/handoff/HANDOFF_20260503.dat",
                List.of());

        when(handoffJobService.getJobStatus(42L)).thenReturn(statusResponse);

        mockMvc.perform(get("/api/handoff/status/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobExecutionId").value(42))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.exitCode").value("COMPLETED"))
                .andExpect(jsonPath("$.outputFilePath").value("/data/handoff/HANDOFF_20260503.dat"))
                .andExpect(jsonPath("$.failureMessages").isEmpty());
    }

    @Test
    void shouldReturn400WhenJobExecutionNotFound() throws Exception {
        when(handoffJobService.getJobStatus(999L))
                .thenThrow(new HandoffException("Job execution not found: 999"));

        mockMvc.perform(get("/api/handoff/status/999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Job execution not found: 999"));
    }

    @Test
    void shouldReturn400WhenJobLaunchFails() throws Exception {
        when(handoffJobService.generateHandoffFile(any()))
                .thenThrow(new HandoffException("Failed to launch handoff job"));

        mockMvc.perform(post("/api/handoff/generate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Failed to launch handoff job"));
    }
}
