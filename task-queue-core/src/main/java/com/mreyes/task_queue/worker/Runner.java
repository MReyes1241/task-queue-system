package com.mreyes.task_queue.worker;

import com.mreyes.task_queue.model.Job;
import com.mreyes.task_queue.service.JobConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class Runner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(Runner.class);
    private final JobConsumer jobConsumer;

    public Runner(JobConsumer jobConsumer) {
        this.jobConsumer = jobConsumer;
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("Worker started, waiting for jobs...");
        while (true) {
            try {
                Job job = jobConsumer.pollJob(10, TimeUnit.SECONDS);
                if (job != null) {
                    jobConsumer.processJob(job);
                } else {
                    logger.debug("No jobs available, waiting...");
                }
            } catch (Exception e) {
                logger.error("Error in worker loop", e);
                Thread.sleep(5000); // Wait before retrying on error
            }
        }
    }
    
}
