package com.banking.handoff.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
class HandoffJobIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Job handoffJob;

    @Autowired
    private JobRepository jobRepository;

    private String tempOutputDir;

    @BeforeEach
    void setUp() throws Exception {
        // Use a synchronous launcher so tests don't need to poll
        TaskExecutorJobLauncher syncLauncher = new TaskExecutorJobLauncher();
        syncLauncher.setJobRepository(jobRepository);
        syncLauncher.afterPropertiesSet();
        jobLauncherTestUtils.setJobLauncher(syncLauncher);
        jobLauncherTestUtils.setJob(handoffJob);

        jobRepositoryTestUtils.removeJobExecutions();

        tempOutputDir = System.getProperty("java.io.tmpdir") + "/handoff-test";
        new File(tempOutputDir).mkdirs();

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS test_accounts (
                    account_no VARCHAR(20) NOT NULL,
                    customer_name VARCHAR(100),
                    balance DECIMAL(15,2),
                    status VARCHAR(10),
                    PRIMARY KEY (account_no)
                )
                """);

        jdbcTemplate.execute("DELETE FROM test_accounts");

        for (int i = 1; i <= 25; i++) {
            jdbcTemplate.update(
                    "INSERT INTO test_accounts (account_no, customer_name, balance, status) VALUES (?, ?, ?, ?)",
                    String.format("ACC%017d", i),
                    "Customer " + i,
                    1000.00 + i,
                    "ACTIVE");
        }
    }

    @AfterEach
    void tearDown() {
        jobRepositoryTestUtils.removeJobExecutions();
    }

    @Test
    void shouldCompleteJobAndProduceCorrectOutputFile() throws Exception {
        String outputFile = tempOutputDir + "/TEST_HANDOFF_INTEGRATION.dat";
        JobParameters params = buildParams(outputFile);

        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(new File(outputFile)).exists();

        List<String> lines = Files.readAllLines(Path.of(outputFile));
        assertThat(lines).hasSize(25);

        String firstLine = lines.get(0);
        assertThat(firstLine).hasSize(75); // 20 + 40 + 15
        assertThat(firstLine.substring(0, 20).strip()).startsWith("ACC");
    }

    @Test
    void shouldCompleteWithEmptyResultSet() throws Exception {
        jdbcTemplate.execute("UPDATE test_accounts SET status = 'INACTIVE'");

        String outputFile = tempOutputDir + "/TEST_HANDOFF_EMPTY.dat";
        JobParameters params = buildParams(outputFile);

        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        List<String> lines = Files.readAllLines(Path.of(outputFile));
        assertThat(lines).isEmpty();
    }

    @Test
    void shouldNotConflictOnMultipleRunsDueToUniqueRunId() throws Exception {
        String outputFile1 = tempOutputDir + "/TEST_HANDOFF_RUN1.dat";
        String outputFile2 = tempOutputDir + "/TEST_HANDOFF_RUN2.dat";

        JobExecution exec1 = jobLauncherTestUtils.launchJob(buildParams(outputFile1));
        JobExecution exec2 = jobLauncherTestUtils.launchJob(buildParams(outputFile2));

        assertThat(exec1.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(exec2.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(exec1.getId()).isNotEqualTo(exec2.getId());
    }

    @Test
    void shouldFormatFirstRecordCorrectly() throws Exception {
        jdbcTemplate.execute("DELETE FROM test_accounts");
        jdbcTemplate.update(
                "INSERT INTO test_accounts (account_no, customer_name, balance, status) VALUES (?, ?, ?, ?)",
                "ACC00000000000000001", "John Smith", 1234.56, "ACTIVE");

        String outputFile = tempOutputDir + "/TEST_HANDOFF_FORMAT.dat";
        JobExecution execution = jobLauncherTestUtils.launchJob(buildParams(outputFile));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        List<String> lines = Files.readAllLines(Path.of(outputFile));
        assertThat(lines).hasSize(1);

        String line = lines.get(0);
        assertThat(line).hasSize(75);

        String accountNo    = line.substring(0, 20);
        String customerName = line.substring(20, 60);
        String balance      = line.substring(60, 75);

        assertThat(accountNo).isEqualTo("ACC00000000000000001");
        assertThat(customerName.strip()).isEqualTo("John Smith");
        assertThat(balance).isEqualTo("000000001234.56");
    }

    private JobParameters buildParams(String outputFilePath) {
        return new JobParametersBuilder()
                .addString("outputFilePath", outputFilePath)
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();
    }
}
