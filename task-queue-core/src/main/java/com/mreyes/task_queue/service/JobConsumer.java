package com.mreyes.task_queue.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mreyes.task_queue.model.Job;
import com.mreyes.task_queue.model.JobHistory;
import com.mreyes.task_queue.repository.JobHistoryRepository;
import com.mreyes.task_queue.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
                logger.info("Job received: {}", job.getId());
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
            logger.info("Processing job: {} - Type: {}", job.getId(), job.getType());

            job.setStatus("PROCESSING");
            job.setStartedAt(LocalDateTime.now());
            jobRepository.save(job);

            logHistory(job.getId(), "PROCESSING", "Job started processing");

            switch (job.getType()) {
                case "send_email":
                    logger.info("Sending email to: {}", job.getData());
                    Thread.sleep(2000);
                    break;

                case "process_image":
                    logger.info("Processing image: {}", job.getData());
                    Thread.sleep(3000);
                    break;

                case "generate_report":
                    logger.info("Generating report: {}", job.getData());
                    Thread.sleep(4000);
                    break;

                case "fail_test":
                    // Job type for testing retry logic
                    throw new RuntimeException("Simulated failure for testing");

                default:
                    logger.info("Processing generic job");
                    Thread.sleep(1000);
            }

            job.setStatus("COMPLETED");
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);

            logHistory(job.getId(), "COMPLETED", "Job completed successfully");
            logger.info("Job completed: {}", job.getId());

        } catch (InterruptedException e) {
            logger.error("Job processing interrupted: {}", job.getId(), e);
            retryService.handleFailedJob(job, "Processing interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Error processing job: {}", job.getId(), e);
            retryService.handleFailedJob(job, e.getMessage());
        }
    }

    private void logHistory(String jobId, String status, String message) {
        try {
            JobHistory history = new JobHistory(jobId, status, message);
            jobHistoryRepository.save(history);
            logger.debug("History logged for job {}: {}", jobId, status);
        } catch (Exception e) {
            logger.error("Failed to log history for job {}: {}", jobId, e.getMessage());
        }
    }
}