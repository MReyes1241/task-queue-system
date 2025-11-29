package com.mreyes.task_queue.controller;

import com.mreyes.task_queue.service.JobProducer;
import com.mreyes.task_queue.model.Job;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobProducer jobProducer;

    public JobController(JobProducer jobProducer) {
        this.jobProducer = jobProducer;
    }

    /**
     * submit a new job to the queue.
     * @POST /api/jobs
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> submitJob(@RequestBody JobRequest request) {
        Job job = new Job(request.getType(), request.getData());
        boolean success = jobProducer.submitJob(job);
        Map<String, Object> response = new HashMap<>();
        if (success) {
            response.put("Worked", true);
            response.put("message", "Job submitted successfully");
            response.put("jobId", job.getId());
            response.put("queueLength", jobProducer.getQueueLength());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } else {
            response.put("Worked", false);
            response.put("message", "Failed to submit job");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get queue status.
     * @GET /api/jobs/queue/status
     */
    @GetMapping("/queue/status")
    public ResponseEntity<Map<String, Object>> getQueueStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("queueLength", jobProducer.getQueueLength());
        status.put("message", "task_queue");
        return ResponseEntity.ok(status);
    }

    /**
     * Health check endpoint.
     * @GET /api/jobs/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> healthStatus = new HashMap<>();
        healthStatus.put("status", "UP");
        healthStatus.put("message", "task-queue-system");
        return ResponseEntity.ok(healthStatus);
    }

    public static class JobRequest{
        private String type;
        private String data;

        public JobRequest() {
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }
    }


}
