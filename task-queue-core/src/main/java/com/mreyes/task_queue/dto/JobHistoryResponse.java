package com.mreyes.task_queue.dto;

import com.mreyes.task_queue.model.JobHistory;
import java.time.LocalDateTime;

public class JobHistoryResponse {
    private Long id;
    private String jobId;
    private String status;
    private String message;
    private LocalDateTime createdAt;

    public JobHistoryResponse(JobHistory history) {
        this.id = history.getId();
        this.jobId = history.getJobId();
        this.status = history.getStatus();
        this.message = history.getMessage();
        this.createdAt = history.getCreatedAt();
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}