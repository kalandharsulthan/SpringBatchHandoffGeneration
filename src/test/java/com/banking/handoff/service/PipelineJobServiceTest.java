package com.banking.handoff.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;

import com.banking.handoff.config.FeedProperties;
import com.banking.handoff.exception.HandoffException;
import com.banking.handoff.service.dto.HandoffStatusResponse;
import com.banking.handoff.service.dto.PipelineJobResponse;
import com.banking.handoff.service.dto.PipelineStatusResponse;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PipelineJobServiceTest {

    @Mock private JobLauncher syncJobLauncher;
    @Mock private Job populationJob;
    @Mock private Job instrumentFeedJob;
    @Mock private Job accountingFeedJob;
    @Mock private JobExplorer jobExplorer;
    @Mock private FeedProperties properties;
    @Mock private FeedProperties.Output output;
    @Mock private FeedProperties.Batch batch;
    @Mock private FeedProperties.FeedConfig instrumentConfig;
    @Mock private FeedProperties.FeedConfig accountingConfig;
    @Mock private FeedProperties.FeedOutput instrumentOutput;
    @Mock private FeedProperties.FeedOutput accountingOutput;

    private PipelineJobService service;

    @BeforeEach
    void setUp() {
        when(properties.getOutput()).thenReturn(output);
        when(output.getDirectory()).thenReturn("/data/handoff");
        when(output.getEncoding()).thenReturn("UTF-8");

        when(properties.getBatch()).thenReturn(batch);
        when(batch.getChunkSize()).thenReturn(1000);

        when(properties.getInstrument()).thenReturn(instrumentConfig);
        when(instrumentConfig.isEnabled()).thenReturn(true);
        when(instrumentConfig.getOutput()).thenReturn(instrumentOutput);
        when(instrumentOutput.getFilePrefix()).thenReturn("INSTRUMENT_FEED_");
        when(instrumentOutput.getFileSuffix()).thenReturn(".dat");

        when(properties.getAccounting()).thenReturn(accountingConfig);
        when(accountingConfig.isEnabled()).thenReturn(true);
        when(accountingConfig.getOutput()).thenReturn(accountingOutput);
        when(accountingOutput.getFilePrefix()).thenReturn("ACCOUNTING_FEED_");
        when(accountingOutput.getFileSuffix()).thenReturn(".csv");

        service = new PipelineJobService(
                syncJobLauncher, populationJob, instrumentFeedJob,
                accountingFeedJob, jobExplorer, properties);
    }

    @Test
    void startPipelineShouldReturnAcceptedResponse() {
        PipelineJobResponse response = service.startPipeline();

        assertThat(response.getRunId()).isNotBlank();
        assertThat(response.getBatchRunId()).isNotBlank();
        assertThat(response.getStatus()).isEqualTo("ACCEPTED");
    }

    @Test
    void runPipelineAsyncShouldCompleteWhenAllJobsSucceed() throws Exception {
        JobExecution completedExec = completedExecution(10L);
        when(syncJobLauncher.run(eq(populationJob), any(JobParameters.class))).thenReturn(completedExec);
        when(syncJobLauncher.run(eq(instrumentFeedJob), any(JobParameters.class))).thenReturn(completedExecution(11L));
        when(syncJobLauncher.run(eq(accountingFeedJob), any(JobParameters.class))).thenReturn(completedExecution(12L));

        PipelineExecution pe = new PipelineExecution("run-1", "batch-1");
        service.runPipelineAsync(pe);

        assertThat(pe.getStatus()).isEqualTo("COMPLETED");
        assertThat(pe.getStagingJobExecutionId()).isEqualTo(10L);
        assertThat(pe.getInstrumentJobExecutionId()).isEqualTo(11L);
        assertThat(pe.getAccountingJobExecutionId()).isEqualTo(12L);
    }

    @Test
    void runPipelineAsyncShouldStopAfterStagingFailure() throws Exception {
        JobExecution failedExec = failedExecution(10L);
        when(syncJobLauncher.run(eq(populationJob), any(JobParameters.class))).thenReturn(failedExec);

        PipelineExecution pe = new PipelineExecution("run-2", "batch-2");
        service.runPipelineAsync(pe);

        assertThat(pe.getStatus()).isEqualTo("FAILED_STAGING");
        verify(syncJobLauncher, never()).run(eq(instrumentFeedJob), any());
        verify(syncJobLauncher, never()).run(eq(accountingFeedJob), any());
    }

    @Test
    void runPipelineAsyncShouldSkipInstrumentWhenDisabled() throws Exception {
        when(instrumentConfig.isEnabled()).thenReturn(false);
        when(syncJobLauncher.run(eq(populationJob), any())).thenReturn(completedExecution(10L));
        when(syncJobLauncher.run(eq(accountingFeedJob), any())).thenReturn(completedExecution(12L));

        PipelineExecution pe = new PipelineExecution("run-3", "batch-3");
        service.runPipelineAsync(pe);

        assertThat(pe.getStatus()).isEqualTo("COMPLETED");
        verify(syncJobLauncher, never()).run(eq(instrumentFeedJob), any());
    }

    @Test
    void getPipelineStatusShouldReturnStatusForKnownRunId() throws Exception {
        when(syncJobLauncher.run(eq(populationJob), any())).thenReturn(completedExecution(1L));
        when(syncJobLauncher.run(eq(instrumentFeedJob), any())).thenReturn(completedExecution(2L));
        when(syncJobLauncher.run(eq(accountingFeedJob), any())).thenReturn(completedExecution(3L));

        PipelineJobResponse response = service.startPipeline();
        String runId = response.getRunId();

        PipelineExecution pe = new PipelineExecution(runId, response.getBatchRunId());
        service.runPipelineAsync(pe);

        PipelineStatusResponse status = service.getPipelineStatus(runId);
        assertThat(status.getRunId()).isEqualTo(runId);
    }

    @Test
    void getPipelineStatusShouldThrowForUnknownRunId() {
        assertThatThrownBy(() -> service.getPipelineStatus("unknown-run-id"))
                .isInstanceOf(HandoffException.class)
                .hasMessageContaining("unknown-run-id");
    }

    @Test
    void getJobStatusShouldReturnStatusForKnownExecutionId() {
        JobParameters params = new org.springframework.batch.core.JobParametersBuilder()
                .addString("outputFilePath", "/data/handoff/TEST.dat")
                .toJobParameters();
        JobExecution execution = new JobExecution(99L, params);
        execution.setStatus(BatchStatus.COMPLETED);
        execution.setExitStatus(ExitStatus.COMPLETED);

        when(jobExplorer.getJobExecution(99L)).thenReturn(execution);

        HandoffStatusResponse response = service.getJobStatus(99L);

        assertThat(response.getJobExecutionId()).isEqualTo(99L);
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getOutputFilePath()).isEqualTo("/data/handoff/TEST.dat");
    }

    @Test
    void getJobStatusShouldThrowForUnknownExecutionId() {
        when(jobExplorer.getJobExecution(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.getJobStatus(999L))
                .isInstanceOf(HandoffException.class)
                .hasMessageContaining("999");
    }

    private JobExecution completedExecution(Long id) {
        JobExecution exec = new JobExecution(id);
        exec.setStatus(BatchStatus.COMPLETED);
        exec.setExitStatus(ExitStatus.COMPLETED);
        return exec;
    }

    private JobExecution failedExecution(Long id) {
        JobExecution exec = new JobExecution(id);
        exec.setStatus(BatchStatus.FAILED);
        exec.setExitStatus(ExitStatus.FAILED);
        return exec;
    }
}
