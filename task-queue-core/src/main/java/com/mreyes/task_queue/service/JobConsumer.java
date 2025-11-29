package com.mreyes.task_queue.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mreyes.task_queue.model.Job;
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

    public JobConsumer(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * poll a job from the queue(blocking).
     * @param timeout The maximum time to wait for a job.
     * @param unit The time unit of the timeout argument.
     * @return The job if available, null otherwise.
     */
    public Job pollJob(long timeout, TimeUnit unit) {
        try {
            String jobJson = (String) redisTemplate.opsForList().rightPop(QUEUE_NAME, timeout, unit);
            if (jobJson != null) {
                Job job = objectMapper.readValue(jobJson, Job.class);
                logger.info("Job polled: {}", job.getId());
                return job;
            } 
            return null;
        }catch (Exception e) {
            logger.error("Failed to poll job from queue", e);
            return null;
        }
    }

    /**
     * Process a job.
     * @param job The job to be processed.
     */
    public void processJob(Job job){
        try{
            logger.info("Processing job: {} - Type: {}", job.getId(), job.getType());
            job.setStatus("IN_PROGRESS");
            switch (job.getType()) {
                case "send_email":
                    logger.info("Sending email with data: {}", job.getData());
                    Thread.sleep(2000); // Simulate email sending(2secs)
                    break;

                case "generate_report":
                    logger.info("Generating report with data: {}", job.getData());
                    Thread.sleep(3000); // Simulate report generation(3secs)
                    break;
                case "process_image":
                    logger.info("Processing image with data: {}", job.getData());
                    Thread.sleep(4000); // Simulate image processing(4secs)
                    break;
                default:
                    logger.info("Working on Misc. Job");
                    Thread.sleep(1000); // Simulate misc. job processing(1sec)
            }

            job.setStatus("COMPLETED");
            logger.info("Job completed: {}", job.getId());


        } catch (InterruptedException e) {
            job.setStatus("FAILED");
            logger.error("Job processing interrupted: {}", job.getId(), e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Failed to process job: {}", job.getId(), e);
            job.setStatus("FAILED");
        }
    }
}
