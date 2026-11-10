package com.hireflow.core.service;

import com.hireflow.core.persistence.JobEntity;
import com.hireflow.core.persistence.JobRepository;
import com.hireflow.core.web.CreateJobRequest;
import com.hireflow.core.web.JobResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JobService {

    private final JobRepository jobs;

    public JobService(JobRepository jobs) {
        this.jobs = jobs;
    }

    @Transactional
    public JobResponse create(CreateJobRequest request) {
        String normalized = Digests.normalizeDescription(request.description());
        JobEntity job = new JobEntity();
        job.setId(UUID.randomUUID());
        job.setTitle(request.title().trim());
        job.setDescription(request.description());
        job.setDescriptionHash(Digests.sha256(normalized));
        job.setCreatedAt(Instant.now());
        return JobResponse.from(jobs.save(job));
    }

    @Transactional(readOnly = true)
    public List<JobResponse> list() {
        return jobs.findAll().stream()
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .map(JobResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public JobResponse get(UUID jobId) {
        return JobResponse.from(require(jobId));
    }

    @Transactional
    public void delete(UUID jobId) {
        jobs.delete(require(jobId));
    }

    public JobEntity require(UUID jobId) {
        return jobs.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
    }
}
