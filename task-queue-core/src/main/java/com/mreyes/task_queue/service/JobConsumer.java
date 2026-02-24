package com.mreyes.task_queue.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mreyes.task_queue.model.Job;
import com.mreyes.task_queue.model.JobHistory;
import com.mreyes.task_queue.repository.JobHistoryRepository;
import com.mreyes.task_queue.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
public class JobConsumer {

    private static final Logger logger = LoggerFactory.getLogger(JobConsumer.class);
    private static final String QUEUE_NAME = "task_queue";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final JobRepository jobRepository;
    private final JobHistoryRepository jobHistoryRepository;
    private final RetryService retryService;

    public JobConsumer(RedisTemplate<String, Object> redisTemplate,
                       ObjectMapper objectMapper,
                       JobRepository jobRepository,
                       JobHistoryRepository jobHistoryRepository,
                       RetryService retryService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.jobRepository = jobRepository;
        this.jobHistoryRepository = jobHistoryRepository;
        this.retryService = retryService;
    }

    public Job pollJob(long timeout, TimeUnit unit) {
        try {
            String jobJson = (String) redisTemplate.opsForList()
                    .rightPop(QUEUE_NAME, timeout, unit);

            if (jobJson != null) {
                Job job = objectMapper.readValue(jobJson, Job.class);
                
                // Add job context to MDC for structured logging
                MDC.put("job_id", job.getId());
                MDC.put("job_type", job.getType());
                
                logger.info("Job received from queue");
                return job;
            }

            return null;
        } catch (Exception e) {
            logger.error("Failed to poll job from queue", e);
            return null;
        }
    }

    public void processJob(Job job) {
        try {
            // Set MDC context for all logs in this method
            MDC.put("job_id", job.getId());
            MDC.put("job_type", job.getType());
            MDC.put("retry_count", String.valueOf(job.getRetryCount()));
            
            logger.info("Processing job started");

            job.setStatus("PROCESSING");
            job.setStartedAt(LocalDateTime.now());
            jobRepository.save(job);

            logHistory(job.getId(), "PROCESSING", "Job started processing");

            switch (job.getType()) {
                case "send_email":
                    logger.debug("Sending email", "recipient", job.getData());
                    Thread.sleep(2000);
                    break;

                case "process_image":
                    logger.debug("Processing image", "image_path", job.getData());
                    Thread.sleep(3000);
                    break;

                case "generate_report":
                    logger.debug("Generating report", "report_type", job.getData());
                    Thread.sleep(4000);
                    break;

                case "fail_test":
                    throw new RuntimeException("Simulated failure for testing");

                default:
                    logger.debug("Processing generic job");
                    Thread.sleep(1000);
            }

            job.setStatus("COMPLETED");
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);

            logHistory(job.getId(), "COMPLETED", "Job completed successfully");
            
            long durationMs = java.time.Duration.between(job.getStartedAt(), job.getCompletedAt()).toMillis();
            MDC.put("duration_ms", String.valueOf(durationMs));
            logger.info("Job completed successfully");

        } catch (InterruptedException e) {
            logger.error("Job processing interrupted", e);
            retryService.handleFailedJob(job, "Processing interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            MDC.put("error_message", e.getMessage());
            logger.error("Job processing failed", e);
            retryService.handleFailedJob(job, e.getMessage());
        } finally {
            // Clean up MDC to prevent memory leaks
            MDC.clear();
        }
    }

    private void logHistory(String jobId, String status, String message) {
        try {
            JobHistory history = new JobHistory(jobId, status, message);
            jobHistoryRepository.save(history);
            logger.debug("History entry saved", "status", status);
        } catch (Exception e) {
            logger.error("Failed to log history for job", e);
        }
    }
}