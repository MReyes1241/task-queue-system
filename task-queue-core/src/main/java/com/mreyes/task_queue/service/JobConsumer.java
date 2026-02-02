package com.mreyes.task_queue.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mreyes.task_queue.model.Job;
import com.mreyes.task_queue.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Service
public class JobConsumer {
    
    private static final Logger logger = LoggerFactory.getLogger(JobConsumer.class);
    private static final String QUEUE_NAME = "task_queue";
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final JobRepository jobRepository; 
    
    public JobConsumer(RedisTemplate<String, Object> redisTemplate, 
                       ObjectMapper objectMapper,
                       JobRepository jobRepository) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.jobRepository = jobRepository;
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
            
            // Update status to PROCESSING in database
            job.setStatus("PROCESSING");
            jobRepository.save(job);
            
            // Simulate work based on job type
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
                    
                default:
                    logger.info(" Processing generic job");
                    Thread.sleep(1000);
            }
            
            // Mark as completed in database
            job.setStatus("COMPLETED");
            jobRepository.save(job);
            logger.info("Job completed: {}", job.getId());
            
        } catch (InterruptedException e) {
            logger.error("Job processing interrupted: {}", job.getId(), e);
            job.setStatus("FAILED");
            jobRepository.save(job);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Error processing job: {}", job.getId(), e);
            job.setStatus("FAILED");
            jobRepository.save(job);
        }
    }
}