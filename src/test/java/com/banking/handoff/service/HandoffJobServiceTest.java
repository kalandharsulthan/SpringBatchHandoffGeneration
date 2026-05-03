package com.banking.handoff.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobLauncher;

import com.banking.handoff.config.HandoffProperties;
import com.banking.handoff.exception.HandoffException;
import com.banking.handoff.service.dto.HandoffJobRequest;
import com.banking.handoff.service.dto.HandoffJobResponse;
import com.banking.handoff.service.dto.HandoffStatusResponse;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HandoffJobServiceTest {

    @Mock
    private JobLauncher asyncJobLauncher;

    @Mock
    private Job handoffJob;

    @Mock
    private JobExplorer jobExplorer;

    @Mock
    private HandoffProperties properties;

    @Mock
    private HandoffProperties.Output outputProps;

    @Mock
    private HandoffProperties.Batch batchProps;

    private HandoffJobService service;

    @BeforeEach
    void setUp() {
        when(properties.getOutput()).thenReturn(outputProps);
        when(outputProps.getDirectory()).thenReturn("/data/handoff");
        when(outputProps.getFilePrefix()).thenReturn("HANDOFF_");
        when(outputProps.getFileSuffix()).thenReturn(".dat");
        service = new HandoffJobService(asyncJobLauncher, handoffJob, jobExplorer, properties);
    }

    @Test
    void shouldLaunchJobWithOutputFilePath() throws Exception {
        JobExecution mockExecution = new JobExecution(42L);
        mockExecution.setStatus(BatchStatus.STARTING);

        when(asyncJobLauncher.run(eq(handoffJob), any(JobParameters.class))).thenReturn(mockExecution);

        HandoffJobResponse response = service.generateHandoffFile(null);

        assertThat(response.getJobExecutionId()).isEqualTo(42L);
        assertThat(response.getStatus()).isEqualTo("STARTING");

        ArgumentCaptor<JobParameters> paramsCaptor = ArgumentCaptor.forClass(JobParameters.class);
        verify(asyncJobLauncher).run(eq(handoffJob), paramsCaptor.capture());

        JobParameters params = paramsCaptor.getValue();
        assertThat(params.getString("outputFilePath")).startsWith("/data/handoff/HANDOFF_");
        assertThat(params.getString("outputFilePath")).endsWith(".dat");
        assertThat(params.getLong("run.id")).isNotNull();
    }

    @Test
    void shouldUseCustomOutputFileNameFromRequest() throws Exception {
        JobExecution mockExecution = new JobExecution(10L);
        mockExecution.setStatus(BatchStatus.STARTING);
        when(asyncJobLauncher.run(eq(handoffJob), any(JobParameters.class))).thenReturn(mockExecution);

        HandoffJobRequest request = new HandoffJobRequest();
        request.setOutputFileName("CUSTOM_FILE.dat");

        service.generateHandoffFile(request);

        ArgumentCaptor<JobParameters> paramsCaptor = ArgumentCaptor.forClass(JobParameters.class);
        verify(asyncJobLauncher).run(eq(handoffJob), paramsCaptor.capture());

        assertThat(paramsCaptor.getValue().getString("outputFilePath"))
                .endsWith("CUSTOM_FILE.dat");
    }

    @Test
    void shouldWrapJobLaunchExceptionInHandoffException() throws Exception {
        when(asyncJobLauncher.run(any(), any()))
                .thenThrow(new JobExecutionAlreadyRunningException("Job already running"));

        assertThatThrownBy(() -> service.generateHandoffFile(null))
                .isInstanceOf(HandoffException.class)
                .hasMessageContaining("Failed to launch handoff job");
    }

    @Test
    void shouldReturnJobStatusForKnownExecution() {
        JobParameters params = new JobParametersBuilder()
                .addString("outputFilePath", "/data/handoff/HANDOFF_20260503.dat")
                .toJobParameters();

        JobExecution execution = new JobExecution(99L, params);
        execution.setStatus(BatchStatus.COMPLETED);
        execution.setExitStatus(ExitStatus.COMPLETED);

        when(jobExplorer.getJobExecution(99L)).thenReturn(execution);

        HandoffStatusResponse response = service.getJobStatus(99L);

        assertThat(response.getJobExecutionId()).isEqualTo(99L);
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getExitCode()).isEqualTo("COMPLETED");
        assertThat(response.getOutputFilePath()).isEqualTo("/data/handoff/HANDOFF_20260503.dat");
        assertThat(response.getFailureMessages()).isEmpty();
    }

    @Test
    void shouldThrowHandoffExceptionForUnknownJobExecutionId() {
        when(jobExplorer.getJobExecution(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.getJobStatus(999L))
                .isInstanceOf(HandoffException.class)
                .hasMessageContaining("999");
    }

    @Test
    void shouldReturnTrueWhenJobIsRunning() {
        JobExecution execution = new JobExecution(5L);
        execution.setStatus(BatchStatus.STARTED);
        when(jobExplorer.getJobExecution(5L)).thenReturn(execution);

        assertThat(service.isJobRunning(5L)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenJobIsNotRunning() {
        JobExecution execution = new JobExecution(6L);
        execution.setStatus(BatchStatus.COMPLETED);
        when(jobExplorer.getJobExecution(6L)).thenReturn(execution);

        assertThat(service.isJobRunning(6L)).isFalse();
    }

    @Test
    void shouldReturnNullTimesWhenExecutionHasNoTimes() {
        JobParameters params = new JobParametersBuilder()
                .addString("outputFilePath", "/data/handoff/HANDOFF_TEST.dat")
                .toJobParameters();

        JobExecution execution = new JobExecution(77L, params);
        execution.setStatus(BatchStatus.STARTED);
        execution.setExitStatus(ExitStatus.UNKNOWN);
        // startTime and endTime are null (job still running)

        when(jobExplorer.getJobExecution(77L)).thenReturn(execution);

        HandoffStatusResponse response = service.getJobStatus(77L);

        assertThat(response.getStartTime()).isNull();
        assertThat(response.getEndTime()).isNull();
    }

    @Test
    void shouldReturnStartAndEndTimeWhenPresent() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("outputFilePath", "/data/handoff/HANDOFF_TEST.dat")
                .toJobParameters();

        JobExecution execution = new JobExecution(88L, params);
        execution.setStatus(BatchStatus.COMPLETED);
        execution.setExitStatus(ExitStatus.COMPLETED);

        // Set start/end times via reflection (SB5 manages them internally)
        java.time.LocalDateTime start = java.time.LocalDateTime.of(2026, 5, 3, 10, 0, 0);
        java.time.LocalDateTime end   = java.time.LocalDateTime.of(2026, 5, 3, 10, 5, 0);
        java.lang.reflect.Field sf = JobExecution.class.getDeclaredField("startTime");
        sf.setAccessible(true);
        sf.set(execution, start);
        java.lang.reflect.Field ef = JobExecution.class.getDeclaredField("endTime");
        ef.setAccessible(true);
        ef.set(execution, end);

        when(jobExplorer.getJobExecution(88L)).thenReturn(execution);

        HandoffStatusResponse response = service.getJobStatus(88L);

        assertThat(response.getStartTime()).isEqualTo(start.toString());
        assertThat(response.getEndTime()).isEqualTo(end.toString());
    }

    @Test
    void shouldIncludeFailureMessagesInStatusResponse() {
        JobParameters params = new JobParametersBuilder()
                .addString("outputFilePath", "/data/handoff/HANDOFF_FAIL.dat")
                .toJobParameters();

        JobExecution execution = new JobExecution(55L, params);
        execution.setStatus(BatchStatus.FAILED);
        execution.setExitStatus(ExitStatus.FAILED);
        execution.addFailureException(new RuntimeException("DB connection lost"));

        when(jobExplorer.getJobExecution(55L)).thenReturn(execution);

        HandoffStatusResponse response = service.getJobStatus(55L);

        assertThat(response.getStatus()).isEqualTo("FAILED");
        assertThat(response.getFailureMessages()).hasSize(1);
        assertThat(response.getFailureMessages().get(0)).isEqualTo("DB connection lost");
    }

    @Test
    void shouldIncludeAdditionalParamsInJobParameters() throws Exception {
        JobExecution mockExecution = new JobExecution(20L);
        mockExecution.setStatus(BatchStatus.STARTING);
        when(asyncJobLauncher.run(eq(handoffJob), any(JobParameters.class))).thenReturn(mockExecution);

        HandoffJobRequest request = new HandoffJobRequest();
        request.setOutputFileName("OUT.dat");
        request.setAdditionalParams(java.util.Map.of("branchCode", "BR001"));

        service.generateHandoffFile(request);

        ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
        verify(asyncJobLauncher).run(eq(handoffJob), captor.capture());

        assertThat(captor.getValue().getString("branchCode")).isEqualTo("BR001");
    }
}
