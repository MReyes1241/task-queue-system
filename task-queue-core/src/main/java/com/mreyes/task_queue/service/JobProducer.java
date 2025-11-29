package com.mreyes.task_queue.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mreyes.task_queue.model.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class JobProducer {
    private static final Logger logger = LoggerFactory.getLogger(JobProducer.class);
    private static final String QUEUE_NAME = "task_queue";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public JobProducer(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Submit a job to the Redis queue.
     * @param job The job to be submitted.
     * @return true if the job was successfully submitted, false otherwise.
     */
    public boolean submitJob(Job job) {
        try {
            String jobJson = objectMapper.writeValueAsString(job);
            redisTemplate.opsForList().leftPush(QUEUE_NAME, jobJson);
            logger.info("Job submitted: {}", job.getId());
            logger.debug("Job details: {}", jobJson);
            return true;
        } catch (Exception e) {
            logger.error("Failed to submit job: {}", job.getId(), e);
            return false;
        }
    }

    /**
     * Get the current queue length.
     * @return The number of jobs in the queue.
     */
    public long getQueueLength() {
        return redisTemplate.opsForList().size(QUEUE_NAME);
    }
}