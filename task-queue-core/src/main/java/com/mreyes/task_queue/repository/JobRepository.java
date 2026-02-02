package com.mreyes.task_queue.repository;

import com.mreyes.task_queue.model.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JobRepository extends JpaRepository<Job, String> {
    
    // JpaRepository provides these methods automatically:
    // - save(job)           // Save or update
    // - findById(id)        // Find by ID
    // - findAll()           // Get all jobs
    // - deleteById(id)      // Delete by ID
    // - count()             // Count total jobs
    
    // Custom query methods (Spring Data JPA will implement automatically):
    Optional<Job> findByStatus(String status);
    
    long countByStatus(String status);
}