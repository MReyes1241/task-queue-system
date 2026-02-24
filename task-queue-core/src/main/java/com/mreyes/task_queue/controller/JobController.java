package com.mreyes.task_queue.controller;

import com.mreyes.task_queue.service.JobProducer;
import com.mreyes.task_queue.dto.JobHistoryResponse;
import com.mreyes.task_queue.dto.JobResponse;
import com.mreyes.task_queue.model.Job;
import com.mreyes.task_queue.model.JobHistory;
import com.mreyes.task_queue.repository.JobHistoryRepository;
import com.mreyes.task_queue.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private static final Logger logger = LoggerFactory.getLogger(JobController.class);

    private final JobProducer jobProducer;
    private final JobRepository jobRepository;
    private final JobHistoryRepository jobHistoryRepository;

    public JobController(JobProducer jobProducer,
                         JobRepository jobRepository,
                         JobHistoryRepository jobHistoryRepository) {
        this.jobProducer = jobProducer;
        this.jobRepository = jobRepository;
        this.jobHistoryRepository = jobHistoryRepository;
    }

    /**
     * submit a new job to the queue.
     * @POST /api/jobs
     */
    @PostMapping
    public ResponseEntity<?> submitJob(@RequestBody Job job) {
        boolean submitted = jobProducer.submitJob(job);
        
        if (submitted) {
            Map<String, String> response = new HashMap<>();
            response.put("jobId", job.getId());
            response.put("status", "submitted");
            logger.info("Job submitted via API: {}", job.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } else {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to submit job");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * Get job details by ID.
     * @GET /api/jobs/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getJob(@PathVariable String id) {
        Optional<Job> jobOpt = jobRepository.findById(id);
        
        if (jobOpt.isEmpty()) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Job not found");
            error.put("jobId", id);
            logger.warn("Job not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
        
        JobResponse response = new JobResponse(jobOpt.get());
        return ResponseEntity.ok(response);
    }

    /**
     * Get complete history of job status transitions.
     * @GET /api/jobs/{id}/history
     */
    @GetMapping("/{id}/history")
    public ResponseEntity<?> getJobHistory(@PathVariable String id) {
        Optional<Job> jobOpt = jobRepository.findById(id);
        
        if (jobOpt.isEmpty()) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Job not found");
            error.put("jobId", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
        
        List<JobHistory> history = jobHistoryRepository.findByJobIdOrderByCreatedAtAsc(id);
        List<JobHistoryResponse> response = history.stream()
                .map(JobHistoryResponse::new)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }

    /**
     * List jobs with optional filtering and pagination.
     * Supports filtering by status and type. Results sorted by creation date descending.
     * @GET /api/jobs?status=FAILED&type=send_email&page=0&size=20
     */
    @GetMapping
    public ResponseEntity<?> listJobs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        // Validate and cap page size
        if (size > 100) {
            size = 100;
        }
        if (size < 1) {
            size = 20;
        }
        
        // Create pageable with sorting by createdAt descending
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        
        Page<Job> jobsPage;
        
        if (status != null && type != null) {
            jobsPage = jobRepository.findByStatusAndType(status, type, pageable);
        } else if (status != null) {
            jobsPage = jobRepository.findByStatus(status, pageable);
        } else if (type != null) {
            jobsPage = jobRepository.findByType(type, pageable);
        } else {
            jobsPage = jobRepository.findAll(pageable);
        }
        
        // Convert to DTOs
        Page<JobResponse> responsePage = jobsPage.map(JobResponse::new);
        
        return ResponseEntity.ok(responsePage);
    }
    
    /**
     * Get system-wide statistics.
     * Returns total job count, counts by status, counts by type, and current queue depth.
     * @GET /api/jobs/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        long total = jobRepository.count();
        
        // Count by status
        Map<String, Long> byStatus = new HashMap<>();
        List<Object[]> statusCounts = jobRepository.countByStatus();
        for (Object[] row : statusCounts) {
            byStatus.put((String) row[0], (Long) row[1]);
        }
        
        // Count by type
        Map<String, Long> byType = new HashMap<>();
        List<Object[]> typeCounts = jobRepository.countByType();
        for (Object[] row : typeCounts) {
            byType.put((String) row[0], (Long) row[1]);
        }
        
        // Get queue depth from Redis
        Long queueDepth = jobProducer.getQueueLength();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("byStatus", byStatus);
        stats.put("byType", byType);
        stats.put("queueDepth", queueDepth);
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Manually retry a failed or dead-lettered job.
     * Resets retry count and re-submits the job. Only works for FAILED or DEAD_LETTER status.
     * @POST /api/jobs/{id}/retry
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<?> retryJob(@PathVariable String id) {
        Optional<Job> jobOpt = jobRepository.findById(id);
        
        if (jobOpt.isEmpty()) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Job not found");
            error.put("jobId", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
        
        Job job = jobOpt.get();
        
        // Only allow retry for FAILED or DEAD_LETTER jobs
        if (!job.getStatus().equals("FAILED") && !job.getStatus().equals("DEAD_LETTER")) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Job is not in FAILED or DEAD_LETTER status");
            error.put("currentStatus", job.getStatus());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
        
        // Reset retry count and re-submit
        job.setRetryCount(0);
        job.setStatus("PENDING");
        job.setNextRetryAt(null);
        job.setErrorMessage(null);
        
        boolean submitted = jobProducer.submitJob(job);
        
        if (submitted) {
            JobHistory history = new JobHistory(job.getId(), "QUEUED", "Manually retried via API");
            jobHistoryRepository.save(history);
            
            Map<String, String> response = new HashMap<>();
            response.put("jobId", job.getId());
            response.put("status", "retried");
            logger.info("Job manually retried via API: {}", job.getId());
            return ResponseEntity.ok(response);
        } else {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to retry job");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * Cancel a pending or queued job.
     * Cannot cancel jobs that are already PROCESSING or COMPLETED.
     * @DELETE /api/jobs/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancelJob(@PathVariable String id) {
        Optional<Job> jobOpt = jobRepository.findById(id);
        
        if (jobOpt.isEmpty()) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Job not found");
            error.put("jobId", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
        
        Job job = jobOpt.get();
        
        // Can only cancel jobs that are PENDING, QUEUED, or FAILED
        if (job.getStatus().equals("PROCESSING") || job.getStatus().equals("COMPLETED")) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Cannot cancel job in " + job.getStatus() + " status");
            error.put("currentStatus", job.getStatus());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
        
        // Mark as cancelled (we'll add this status)
        job.setStatus("CANCELLED");
        jobRepository.save(job);
        
        JobHistory history = new JobHistory(job.getId(), "CANCELLED", "Cancelled via API");
        jobHistoryRepository.save(history);
        
        Map<String, String> response = new HashMap<>();
        response.put("jobId", job.getId());
        response.put("status", "cancelled");
        logger.info("Job cancelled via API: {}", job.getId());
        
        return ResponseEntity.ok(response);
    }
}