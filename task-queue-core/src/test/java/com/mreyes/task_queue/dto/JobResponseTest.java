package com.mreyes.task_queue.dto;

import com.mreyes.task_queue.model.Job;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class JobResponseTest {

    @Test
    void constructor_mapsAllFieldsFromJob() {
        // Given
        Job job = new Job();
        job.setId("test-123");
        job.setType("send_email");
        job.setStatus("COMPLETED");
        job.setData("test@example.com");
        job.setPriority(1);
        job.setMaxRetries(3);
        job.setRetryCount(0);
        
        LocalDateTime now = LocalDateTime.now();
        job.setCreatedAt(now);
        job.setQueuedAt(now);
        job.setStartedAt(now);
        job.setCompletedAt(now);

        // When
        JobResponse response = new JobResponse(job);

        // Then
        assertThat(response.getId()).isEqualTo("test-123");
        assertThat(response.getType()).isEqualTo("send_email");
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getData()).isEqualTo("test@example.com");
        assertThat(response.getPriority()).isEqualTo(1);
        assertThat(response.getMaxRetries()).isEqualTo(3);
        assertThat(response.getRetryCount()).isEqualTo(0);
        assertThat(response.getCreatedAt()).isEqualTo(now);
        assertThat(response.getCompletedAt()).isEqualTo(now);
    }

    @Test
    void constructor_handlesNullTimestamps() {
        // Given
        Job job = new Job();
        job.setId("test-123");
        job.setType("send_email");
        job.setStatus("QUEUED");

        // When
        JobResponse response = new JobResponse(job);

        // Then
        assertThat(response.getCompletedAt()).isNull();
        assertThat(response.getStartedAt()).isNull();
    }
}