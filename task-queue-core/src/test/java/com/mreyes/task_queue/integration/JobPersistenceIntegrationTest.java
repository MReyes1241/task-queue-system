package com.mreyes.task_queue.integration;

import com.mreyes.task_queue.model.Job;
import com.mreyes.task_queue.model.JobHistory;
import com.mreyes.task_queue.repository.JobHistoryRepository;
import com.mreyes.task_queue.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Disabled;


@Disabled("Requires Docker/Testcontainers - Docker socket configuration issue on macOS")
@SpringBootTest
@Testcontainers
class JobPersistenceIntegrationTest {

    // Add generic type parameter to fix the warning
    @Container
    @SuppressWarnings("resource") // container managed by Testcontainers framework
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobHistoryRepository jobHistoryRepository;

    @Test
    void saveJob_persistsAllFields() {
        // Given
        Job job = new Job();
        job.setType("send_email");
        job.setData("test@example.com");
        job.setStatus("QUEUED");
        job.setPriority(0);
        job.setMaxRetries(3);
        job.setRetryCount(0);
        job.setCreatedAt(LocalDateTime.now());

        // When
        Job saved = jobRepository.save(job);

        // Then
        assertThat(saved.getId()).isNotNull();
        
        Optional<Job> found = jobRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getType()).isEqualTo("send_email");
        assertThat(found.get().getStatus()).isEqualTo("QUEUED");
    }

    @Test
    void saveJobHistory_createsAuditTrail() {
        // Given
        Job job = new Job();
        job.setType("send_email");
        job.setStatus("QUEUED");
        Job savedJob = jobRepository.save(job);

        JobHistory history = new JobHistory(savedJob.getId(), "QUEUED", "Job submitted");

        // When
        jobHistoryRepository.save(history);

        // Then
        List<JobHistory> found = jobHistoryRepository.findByJobIdOrderByCreatedAtAsc(savedJob.getId());
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getStatus()).isEqualTo("QUEUED");
        assertThat(found.get(0).getMessage()).isEqualTo("Job submitted");
    }

    @Test
    void findByStatus_filtersJobsCorrectly() {
        // Given
        Job job1 = new Job();
        job1.setType("send_email");
        job1.setStatus("COMPLETED");
        jobRepository.save(job1);

        Job job2 = new Job();
        job2.setType("process_image");
        job2.setStatus("FAILED");
        jobRepository.save(job2);

        // When
        long failedCount = jobRepository.countByStatus("FAILED");
        long completedCount = jobRepository.countByStatus("COMPLETED");

        // Then
        assertThat(failedCount).isGreaterThanOrEqualTo(1);
        assertThat(completedCount).isGreaterThanOrEqualTo(1);
    }
}