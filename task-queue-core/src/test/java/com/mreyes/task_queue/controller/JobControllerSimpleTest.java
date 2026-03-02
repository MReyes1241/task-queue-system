package com.mreyes.task_queue.controller;

import com.mreyes.task_queue.model.Job;
import com.mreyes.task_queue.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.junit.jupiter.api.Disabled;


import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("Requires Docker/Testcontainers - Docker socket configuration issue on macOS")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@SuppressWarnings("resource")
class JobControllerSimpleTest {

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

    @LocalServerPort
    private int port;

    @Autowired
    private JobRepository jobRepository;

    private HttpClient httpClient;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        baseUrl = "http://localhost:" + port + "/api/jobs";
        jobRepository.deleteAll();
    }

    @Test
    void submitJob_returnsSuccessResponse() throws Exception {
        // Given
        String requestBody = """
            {
                "type": "send_email",
                "data": "test@example.com"
            }
            """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        // When
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("jobId");
        assertThat(response.body()).contains("submitted");
    }

    @Test
    void submitJob_withMissingType_returnsBadRequest() throws Exception {
        // Given
        String requestBody = """
            {
                "data": "test@example.com"
            }
            """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        // When
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Then
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void getJob_returnsJobDetails() throws Exception {
        // Given - create a job directly in database
        Job job = new Job();
        job.setType("send_email");
        job.setData("test@example.com");
        job.setStatus("QUEUED");
        Job saved = jobRepository.save(job);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/" + saved.getId()))
                .GET()
                .build();

        // When
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(saved.getId());
        assertThat(response.body()).contains("send_email");
        assertThat(response.body()).contains("QUEUED");
    }

    @Test
    void getJob_withNonExistentId_returnsNotFound() throws Exception {
        // Given
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/nonexistent-id"))
                .GET()
                .build();

        // When
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Then
        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void listJobs_returnsPaginatedResults() throws Exception {
        // Given - create 5 jobs
        for (int i = 0; i < 5; i++) {
            Job job = new Job();
            job.setType("send_email");
            job.setStatus("QUEUED");
            jobRepository.save(job);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "?page=0&size=3"))
                .GET()
                .build();

        // When
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("totalElements");
        assertThat(response.body()).contains("totalPages");
        assertThat(response.body()).contains("content");
    }

    @Test
    void listJobs_filtersByStatus() throws Exception {
        // Given
        Job completedJob = new Job();
        completedJob.setType("send_email");
        completedJob.setStatus("COMPLETED");
        jobRepository.save(completedJob);

        Job failedJob = new Job();
        failedJob.setType("send_email");
        failedJob.setStatus("FAILED");
        jobRepository.save(failedJob);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "?status=FAILED"))
                .GET()
                .build();

        // When
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("FAILED");
        assertThat(response.body()).doesNotContain("COMPLETED");
    }

    @Test
    void getStats_returnsSystemStatistics() throws Exception {
        // Given
        Job completed = new Job();
        completed.setType("send_email");
        completed.setStatus("COMPLETED");
        jobRepository.save(completed);

        Job failed = new Job();
        failed.setType("process_image");
        failed.setStatus("FAILED");
        jobRepository.save(failed);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/stats"))
                .GET()
                .build();

        // When
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("total");
        assertThat(response.body()).contains("byStatus");
        assertThat(response.body()).contains("byType");
    }

    @Test
    void retryJob_withFailedJob_requeuesJob() throws Exception {
        // Given
        Job job = new Job();
        job.setType("send_email");
        job.setStatus("FAILED");
        job.setRetryCount(2);
        job.setErrorMessage("Previous error");
        Job saved = jobRepository.save(job);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/" + saved.getId() + "/retry"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        // When
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Then
        assertThat(response.statusCode()).isEqualTo(200);
        
        // Verify job was updated
        Job updated = jobRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("QUEUED");
        assertThat(updated.getRetryCount()).isEqualTo(0);
    }

    @Test
    void retryJob_withCompletedJob_returnsBadRequest() throws Exception {
        // Given
        Job job = new Job();
        job.setType("send_email");
        job.setStatus("COMPLETED");
        Job saved = jobRepository.save(job);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/" + saved.getId() + "/retry"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        // When
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Then
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("not in FAILED or DEAD_LETTER");
    }

    @Test
    void cancelJob_withQueuedJob_cancelsJob() throws Exception {
        // Given
        Job job = new Job();
        job.setType("send_email");
        job.setStatus("QUEUED");
        Job saved = jobRepository.save(job);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/" + saved.getId()))
                .DELETE()
                .build();

        // When
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Then
        assertThat(response.statusCode()).isEqualTo(200);
        
        // Verify job was cancelled
        Job updated = jobRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void cancelJob_withCompletedJob_returnsBadRequest() throws Exception {
        // Given
        Job job = new Job();
        job.setType("send_email");
        job.setStatus("COMPLETED");
        Job saved = jobRepository.save(job);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/" + saved.getId()))
                .DELETE()
                .build();

        // When
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Then
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Cannot cancel");
    }

    @Test
    void getHealth_returnsOk() throws Exception {
        // Given
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/actuator/health"))
                .GET()
                .build();

        // When
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("UP");
    }

    @Test
    void getJobHistory_returnsAuditTrail() throws Exception {
        // Given
        Job job = new Job();
        job.setType("send_email");
        job.setStatus("COMPLETED");
        Job saved = jobRepository.save(job);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/" + saved.getId() + "/history"))
                .GET()
                .build();

        // When
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Then
        assertThat(response.statusCode()).isEqualTo(200);
        // History will be empty in this test since we created job directly
        // But endpoint should still return 200
    }
}