package com.mreyes.task_queue.service;

import com.mreyes.task_queue.model.Job;
import com.mreyes.task_queue.repository.JobHistoryRepository;
import com.mreyes.task_queue.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetryServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobHistoryRepository jobHistoryRepository;

    private RetryService retryService;

    @BeforeEach
    void setUp() {
        retryService = new RetryService(jobRepository, jobHistoryRepository);
    }

    @Test
    void shouldRetry_whenRetryCountBelowMax_returnsTrue() {
        // Given
        Job job = new Job();
        job.setMaxRetries(3);
        job.setRetryCount(1);

        // When
        boolean result = shouldRetry(job);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void shouldRetry_whenRetryCountEqualsMax_returnsFalse() {
        // Given
        Job job = new Job();
        job.setMaxRetries(3);
        job.setRetryCount(3);

        // When
        boolean result = shouldRetry(job);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void calculateDelay_withRetryCount0_returns2Seconds() throws Exception {
        // When
        long delay = calculateDelay(0);

        // Then
        assertThat(delay).isBetween(2L, 3L); // 2s base + up to 1s jitter
    }

    @Test
    void calculateDelay_withRetryCount1_returns4Seconds() throws Exception {
        // When
        long delay = calculateDelay(1);

        // Then
        assertThat(delay).isBetween(4L, 5L); // 4s base + up to 1s jitter
    }

    @Test
    void calculateDelay_withRetryCount2_returns8Seconds() throws Exception {
        // When
        long delay = calculateDelay(2);

        // Then
        assertThat(delay).isBetween(8L, 9L); // 8s base + up to 1s jitter
    }

    @Test
    void calculateDelay_withHighRetryCount_isCappedAt60Seconds() throws Exception {
        // When
        long delay = calculateDelay(10); // Would be 1024s without cap

        // Then
        assertThat(delay).isLessThanOrEqualTo(60L);
    }

    @Test
    void handleFailedJob_whenRetriesRemain_schedulesRetry() {
        // Given
        Job job = new Job();
        job.setId("test-job");
        job.setMaxRetries(3);
        job.setRetryCount(0);
        String errorMessage = "Test error";

        // When
        retryService.handleFailedJob(job, errorMessage);

        // Then
        assertThat(job.getStatus()).isEqualTo("FAILED");
        assertThat(job.getRetryCount()).isEqualTo(1);
        assertThat(job.getNextRetryAt()).isNotNull();
        assertThat(job.getErrorMessage()).isEqualTo(errorMessage);
        verify(jobRepository).save(job);
        verify(jobHistoryRepository).save(any());
    }

    @Test
    void handleFailedJob_whenRetriesExhausted_movesToDeadLetter() {
        // Given
        Job job = new Job();
        job.setId("test-job");
        job.setMaxRetries(3);
        job.setRetryCount(3);
        String errorMessage = "Test error";

        // When
        retryService.handleFailedJob(job, errorMessage);

        // Then
        assertThat(job.getStatus()).isEqualTo("DEAD_LETTER");
        assertThat(job.getErrorMessage()).isEqualTo(errorMessage);
        verify(jobRepository).save(job);
        verify(jobHistoryRepository).save(any());
    }

    // Helper methods to access private methods via reflection (for testing)
    private boolean shouldRetry(Job job) {
        try {
            Method method = RetryService.class.getDeclaredMethod("shouldRetry", Job.class);
            method.setAccessible(true);
            return (boolean) method.invoke(retryService, job);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private long calculateDelay(int retryCount) {
        try {
            Method method = RetryService.class.getDeclaredMethod("calculateDelay", int.class);
            method.setAccessible(true);
            return (long) method.invoke(retryService, retryCount);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}