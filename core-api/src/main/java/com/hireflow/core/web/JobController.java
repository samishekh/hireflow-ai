package com.hireflow.core.web;

import com.hireflow.core.service.CandidateQueryService;
import com.hireflow.core.service.JobService;
import com.hireflow.core.service.UploadService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class JobController {

    private final JobService jobs;
    private final UploadService uploads;
    private final CandidateQueryService candidates;

    public JobController(
            JobService jobs,
            UploadService uploads,
            CandidateQueryService candidates) {
        this.jobs = jobs;
        this.uploads = uploads;
        this.candidates = candidates;
    }

    @PostMapping("/api/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public JobResponse create(@Valid @RequestBody CreateJobRequest request) {
        return jobs.create(request);
    }

    @GetMapping("/api/jobs")
    public List<JobResponse> list() {
        return jobs.list();
    }

    @GetMapping("/api/jobs/{id}")
    public JobResponse get(@PathVariable UUID id) {
        return jobs.get(id);
    }

    @PostMapping(path = "/api/jobs/{id}/candidates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> upload(
            @PathVariable UUID id,
            @RequestParam("files") List<MultipartFile> files) {
        return ResponseEntity.accepted().body(new UploadResponse(uploads.accept(id, files)));
    }

    @GetMapping("/api/jobs/{id}/rankings")
    public List<RankingResponse> rankings(@PathVariable UUID id) {
        return candidates.rankings(id);
    }
}
