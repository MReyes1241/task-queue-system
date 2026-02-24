package com.mreyes.task_queue.service;

import com.mreyes.task_queue.model.Job;
import com.mreyes.task_queue.model.JobHistory;
import com.mreyes.task_queue.repository.JobHistoryRepository;
import com.mreyes.task_queue.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class RetryService {

    private static final Logger logger = LoggerFactory.getLogger(RetryService.class);
    private static final long BASE_DELAY_SECONDS = 2;
    private static final long MAX_DELAY_SECONDS = 60;

    private final JobRepository jobRepository;
    private final JobHistoryRepository jobHistoryRepository;

    public RetryService(JobRepository jobRepository,
                        JobHistoryRepository jobHistoryRepository) {
        this.jobRepository = jobRepository;
        this.jobHistoryRepository = jobHistoryRepository;
    }

    public void handleFailedJob(Job job, String errorMessage) {
        // Set MDC context
        MDC.put("job_id", job.getId());
        MDC.put("job_type", job.getType());
        MDC.put("retry_count", String.valueOf(job.getRetryCount()));
        
        job.setErrorMessage(errorMessage);

        if (shouldRetry(job)) {
            scheduleRetry(job);
        } else {
            sendToDeadLetter(job);
        }

        jobRepository.save(job);
        MDC.clear();
    }

    private boolean shouldRetry(Job job) {
        return job.getRetryCount() < job.getMaxRetries();
    }

    private void scheduleRetry(Job job) {
        int currentRetryCount = job.getRetryCount();
        long delaySeconds = calculateDelay(currentRetryCount);

        job.setRetryCount(currentRetryCount + 1);
        job.setStatus("FAILED");
        job.setNextRetryAt(LocalDateTime.now().plusSeconds(delaySeconds));

        MDC.put("retry_attempt", String.valueOf(job.getRetryCount()));
        MDC.put("max_retries", String.valueOf(job.getMaxRetries()));
        MDC.put("next_retry_delay_seconds", String.valueOf(delaySeconds));
        
        logger.info("Job scheduled for retry");

        JobHistory history = new JobHistory(
                job.getId(),
                "FAILED",
                "Attempt " + job.getRetryCount() + " failed. Retrying in "
                        + delaySeconds + " seconds. Error: " + job.getErrorMessage()
        );
        jobHistoryRepository.save(history);
    }

    private void sendToDeadLetter(Job job) {
        job.setStatus("DEAD_LETTER");

        MDC.put("final_retry_count", String.valueOf(job.getRetryCount()));
        logger.error("Job moved to dead letter queue after exhausting retries");

        JobHistory history = new JobHistory(
                job.getId(),
                "DEAD_LETTER",
                "Max retries (" + job.getMaxRetries() + ") exhausted. "
                        + "Final error: " + job.getErrorMessage()
        );
        jobHistoryRepository.save(history);
    }

    private long calculateDelay(int retryCount) {
        // Exponential backoff: baseDelay * 2^retryCount
        long delay = BASE_DELAY_SECONDS * (long) Math.pow(2, retryCount);

        // Add jitter: random value between 0 and 1000 milliseconds
        long jitterSeconds = (long) (Math.random() * 1);

        // Cap at max delay
        return Math.min(delay + jitterSeconds, MAX_DELAY_SECONDS);
    }
}