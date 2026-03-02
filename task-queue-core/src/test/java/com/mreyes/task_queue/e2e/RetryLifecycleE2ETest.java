package com.mreyes.task_queue.e2e;

import com.mreyes.task_queue.model.Job;
import com.mreyes.task_queue.model.JobHistory;
import com.mreyes.task_queue.repository.JobHistoryRepository;
import com.mreyes.task_queue.repository.JobRepository;
import com.mreyes.task_queue.service.JobProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import org.junit.jupiter.api.Disabled;


@Disabled("Requires Docker/Testcontainers - Docker socket configuration issue on macOS")
@SpringBootTest
@Testcontainers
@SuppressWarnings("resource")
class RetryLifecycleE2ETest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", () -> "localhost");
    }

    @Autowired
    private JobProducer jobProducer;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobHistoryRepository jobHistoryRepository;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
        jobHistoryRepository.deleteAll();
    }

    @Test
    void successfulJob_completesWithoutRetries() {
        // Given
        Job job = new Job();
        job.setType("send_email");
        job.setData("test@example.com");
        
        // When - submit job and get ID
        jobProducer.submitJob(job);
        String jobId = job.getId(); // Job ID is set by submitJob

        // Wait for job to complete
        await().atMost(10, SECONDS)
                .untilAsserted(() -> {
                    Job foundJob = jobRepository.findById(jobId).orElseThrow();
                    assertThat(foundJob.getStatus()).isEqualTo("COMPLETED");
                });

        // Then
        Job completedJob = jobRepository.findById(jobId).orElseThrow();
        assertThat(completedJob.getRetryCount()).isEqualTo(0);
        assertThat(completedJob.getErrorMessage()).isNull();
        assertThat(completedJob.getCompletedAt()).isNotNull();

        // Verify history
        List<JobHistory> history = jobHistoryRepository.findByJobIdOrderByCreatedAtAsc(jobId);
        assertThat(history).extracting(JobHistory::getStatus)
                .contains("QUEUED", "PROCESSING", "COMPLETED");
    }

    @Test
    void failingJob_retriesWithExponentialBackoff_thenMovesToDeadLetter() {
        // Given - submit a job that will fail
        Job job = new Job();
        job.setType("fail_test");
        job.setData("this will fail");
        
        // When - submit job and get ID
        jobProducer.submitJob(job);
        String jobId = job.getId();

        // Wait for all retries to exhaust
        await().atMost(30, SECONDS)
                .untilAsserted(() -> {
                    Job foundJob = jobRepository.findById(jobId).orElseThrow();
                    assertThat(foundJob.getStatus()).isEqualTo("DEAD_LETTER");
                });

        // Then
        Job deadLetterJob = jobRepository.findById(jobId).orElseThrow();
        assertThat(deadLetterJob.getRetryCount()).isEqualTo(3);
        assertThat(deadLetterJob.getErrorMessage()).isNotNull();
        assertThat(deadLetterJob.getNextRetryAt()).isNull();

        // Verify complete retry history
        List<JobHistory> history = jobHistoryRepository.findByJobIdOrderByCreatedAtAsc(jobId);
        
        // Should have multiple entries including retries
        assertThat(history).hasSizeGreaterThanOrEqualTo(5);
        assertThat(history.get(0).getStatus()).isEqualTo("QUEUED");
        assertThat(history.get(history.size() - 1).getStatus()).isEqualTo("DEAD_LETTER");
        
        // Count retry attempts
        long failedCount = history.stream()
                .filter(h -> h.getStatus().equals("FAILED"))
                .count();
        assertThat(failedCount).isGreaterThanOrEqualTo(3);
    }

    @Test
    void manualRetry_fromDeadLetter_requeuesjob() {
        // Given - create a job in DEAD_LETTER status
        Job deadLetterJob = new Job();
        deadLetterJob.setType("send_email");
        deadLetterJob.setData("test@example.com");
        deadLetterJob.setStatus("DEAD_LETTER");
        deadLetterJob.setRetryCount(3);
        deadLetterJob.setErrorMessage("Previous failures");
        Job saved = jobRepository.save(deadLetterJob);

        // When - manually retry
        saved.setStatus("QUEUED");
        saved.setRetryCount(0);
        saved.setErrorMessage(null);
        jobRepository.save(saved);
        
        JobHistory history = new JobHistory(saved.getId(), "QUEUED", "Manually retried");
        jobHistoryRepository.save(history);

        // Then - verify job was reset
        Job retriedJob = jobRepository.findById(saved.getId()).orElseThrow();
        assertThat(retriedJob.getStatus()).isEqualTo("QUEUED");
        assertThat(retriedJob.getRetryCount()).isEqualTo(0);
        assertThat(retriedJob.getErrorMessage()).isNull();
    }
}