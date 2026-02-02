package com.mreyes.task_queue.repository;

import com.mreyes.task_queue.model.JobHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobHistoryRepository extends JpaRepository<JobHistory, Long> {
    
    // Find all history entries for a specific job
    List<JobHistory> findByJobIdOrderByCreatedAtAsc(String jobId);
    
    // Count history entries for a job
    long countByJobId(String jobId);
}