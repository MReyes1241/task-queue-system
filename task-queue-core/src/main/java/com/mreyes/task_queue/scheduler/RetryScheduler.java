package com.mreyes.task_queue.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mreyes.task_queue.model.Job;
import com.mreyes.task_queue.model.JobHistory;
import com.mreyes.task_queue.repository.JobHistoryRepository;
import com.mreyes.task_queue.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class RetryScheduler {

    private static final Logger logger = LoggerFactory.getLogger(RetryScheduler.class);
    private static final String QUEUE_NAME = "task_queue";

    private final JobRepository jobRepository;
    private final JobHistoryRepository jobHistoryRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public RetryScheduler(JobRepository jobRepository,
                          JobHistoryRepository jobHistoryRepository,
                          RedisTemplate<String, Object> redisTemplate,
                          ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.jobHistoryRepository = jobHistoryRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // Runs every 10 seconds
    @Scheduled(fixedDelay = 10000)
    public void requeueFailedJobs() {
        List<Job> jobsReadyToRetry = jobRepository.findJobsReadyToRetry(LocalDateTime.now());

        if (!jobsReadyToRetry.isEmpty()) {
            logger.info("Found {} jobs ready to retry", jobsReadyToRetry.size());
        }

        for (Job job : jobsReadyToRetry) {
            try {
                job.setStatus("QUEUED");
                job.setQueuedAt(LocalDateTime.now());
                job.setNextRetryAt(null);
                jobRepository.save(job);

                String jobJson = objectMapper.writeValueAsString(job);
                redisTemplate.opsForList().leftPush(QUEUE_NAME, jobJson);

                JobHistory history = new JobHistory(
                        job.getId(),
                        "QUEUED",
                        "Retry attempt " + job.getRetryCount() + " queued"
                );
                jobHistoryRepository.save(history);

                logger.info("Re-queued job {} for retry attempt {}",
                        job.getId(), job.getRetryCount());

            } catch (Exception e) {
                logger.error("Failed to re-queue job {} for retry: {}",
                        job.getId(), e.getMessage());
            }
        }
    }
}