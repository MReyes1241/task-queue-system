package com.mreyes.task_queue.dto;

import com.mreyes.task_queue.model.Job;
import java.time.LocalDateTime;

public class JobResponse {
    private String id;
    private String type;
    private String status;
    private String data;
    private Integer priority;
    private Integer maxRetries;
    private Integer retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime queuedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime nextRetryAt;
    private String errorMessage;

    // Constructor that takes a Job entity
    public JobResponse(Job job) {
        this.id = job.getId();
        this.type = job.getType();
        this.status = job.getStatus();
        this.data = job.getData();
        this.priority = job.getPriority();
        this.maxRetries = job.getMaxRetries();
        this.retryCount = job.getRetryCount();
        this.createdAt = job.getCreatedAt();
        this.queuedAt = job.getQueuedAt();
        this.startedAt = job.getStartedAt();
        this.completedAt = job.getCompletedAt();
        this.nextRetryAt = job.getNextRetryAt();
        this.errorMessage = job.getErrorMessage();
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
    
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    
    public Integer getMaxRetries() { return maxRetries; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }
    
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getQueuedAt() { return queuedAt; }
    public void setQueuedAt(LocalDateTime queuedAt) { this.queuedAt = queuedAt; }
    
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    
    public LocalDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(LocalDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}