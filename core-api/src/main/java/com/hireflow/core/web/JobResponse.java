package com.hireflow.core.web;

import com.hireflow.core.persistence.JobEntity;
import java.time.Instant;
import java.util.UUID;

public record JobResponse(UUID id, String title, String description, Instant createdAt) {

    public static JobResponse from(JobEntity job) {
        return new JobResponse(job.getId(), job.getTitle(), job.getDescription(), job.getCreatedAt());
    }
}
