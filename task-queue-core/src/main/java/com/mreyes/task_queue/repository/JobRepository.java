package com.mreyes.task_queue.repository;

import com.mreyes.task_queue.model.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
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

    // Find jobs that failed and whose retry delay has expired
    @Query("SELECT j FROM Job j WHERE j.status = 'FAILED' " +
           "AND j.nextRetryAt IS NOT NULL " +
           "AND j.nextRetryAt <= :now")
    List<Job> findJobsReadyToRetry(@Param("now") LocalDateTime now);

    // Find all jobs with pagination
    Page<Job> findAll(Pageable pageable);
    
    // Find by status with pagination
    Page<Job> findByStatus(String status, Pageable pageable);
    
    // Find by type with pagination
    Page<Job> findByType(String type, Pageable pageable);
    
    // Find by status AND type with pagination
    Page<Job> findByStatusAndType(String status, String type, Pageable pageable);
    
    long countByType(String type);
    
    long countByStatusAndType(String status, String type);
    
    @Query("SELECT j.status, COUNT(j) FROM Job j GROUP BY j.status")
    List<Object[]> countByStatus();
    
    @Query("SELECT j.type, COUNT(j) FROM Job j GROUP BY j.type")
    List<Object[]> countByType();

}