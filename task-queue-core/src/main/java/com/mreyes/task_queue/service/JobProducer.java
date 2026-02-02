package com.mreyes.task_queue.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mreyes.task_queue.model.Job;
import com.mreyes.task_queue.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class JobProducer {
    
    private static final Logger logger = LoggerFactory.getLogger(JobProducer.class);
    private static final String QUEUE_NAME = "task_queue";
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final JobRepository jobRepository;
    
    public JobProducer(RedisTemplate<String, Object> redisTemplate, 
                       ObjectMapper objectMapper,
                       JobRepository jobRepository) { 
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.jobRepository = jobRepository;
    }
    
    public boolean submitJob(Job job) {
        try {
            // Save to database FIRST
            job.setStatus("QUEUED");
            job.setCreatedAt(LocalDateTime.now());
            jobRepository.save(job);
            logger.info("Job saved to database: {}", job.getId());
            
            // Then push to Redis queue
            String jobJson = objectMapper.writeValueAsString(job);
            redisTemplate.opsForList().leftPush(QUEUE_NAME, jobJson);
            logger.info("Job queued in Redis: {}", job.getId());
            
            return true;
        } catch (Exception e) {
            logger.error("Failed to submit job: {}", job.getId(), e);
            return false;
        }
    }
    
    public Long getQueueLength() {
        return redisTemplate.opsForList().size(QUEUE_NAME);
    }
}