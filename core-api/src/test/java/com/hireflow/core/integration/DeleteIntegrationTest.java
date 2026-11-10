package com.hireflow.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.hireflow.core.persistence.CandidateRepository;
import com.hireflow.core.persistence.JobRepository;
import com.hireflow.core.persistence.MatchResultRepository;
import com.hireflow.core.web.RankingResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class DeleteIntegrationTest extends PipelineTestSupport {

    @Autowired
    private JobRepository jobs;

    @Autowired
    private CandidateRepository candidates;

    @Autowired
    private MatchResultRepository matches;

    @Test
    void deleteCandidateRemovesScoreAndDecisionAndLeavesTheRest() {
        String kept = "delete-kept-skill";
        String dropped = "delete-dropped-skill";
        stubParseAndScore(kept, 90, 80, 70, 85);
        stubParseAndScore(dropped, 40, 30, 20, 35);

        UUID jobId = createJob("Delete one candidate");
        UUID otherJobId = createJob("Untouched role");
        List<UUID> ids = upload(jobId, pdf("kept.pdf", kept), pdf("dropped.pdf", dropped));
        UUID keptId = ids.get(0);
        UUID droppedId = ids.get(1);
        UUID otherId = upload(otherJobId, pdf("other.pdf", kept)).getFirst();

        assertDone(awaitTerminal(keptId));
        assertDone(awaitTerminal(droppedId));
        assertDone(awaitTerminal(otherId));
        assertThat(postOverride(droppedId).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(postOverride(otherId).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> missing = delete("/api/candidates/{id}", UUID.randomUUID());
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> deleted = delete("/api/candidates/{id}", droppedId);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(deleted.getBody()).isNull();

        assertThat(delete("/api/candidates/{id}", droppedId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(candidates.findById(droppedId)).isEmpty();
        assertThat(matches.findById(droppedId)).isEmpty();
        assertThat(overrides.findByCandidateIdOrderByCreatedAtDesc(droppedId)).isEmpty();

        assertThat(candidates.findById(keptId)).isPresent();
        assertThat(matches.findById(keptId)).isPresent();
        assertThat(jobs.findById(jobId)).isPresent();
        List<RankingResponse> remaining = rankings(jobId);
        assertThat(remaining).extracting(RankingResponse::id).containsExactly(keptId);

        assertThat(candidates.findById(otherId)).isPresent();
        assertThat(matches.findById(otherId)).isPresent();
        assertThat(overrides.findByCandidateIdOrderByCreatedAtDesc(otherId)).hasSize(1);
    }

    @Test
    void deleteJobRemovesEveryCandidateScoreAndDecision() {
        String skill = "delete-job-skill";
        stubParseAndScore(skill, 80, 70, 60, 74.5);

        UUID jobId = createJob("Delete the role");
        UUID otherJobId = createJob("Role that stays");
        List<UUID> ids = upload(jobId, pdf("one.pdf", skill), pdf("two.pdf", skill));
        UUID firstId = ids.get(0);
        UUID secondId = ids.get(1);
        UUID otherId = upload(otherJobId, pdf("stays.pdf", skill)).getFirst();

        assertDone(awaitTerminal(firstId));
        assertDone(awaitTerminal(secondId));
        assertDone(awaitTerminal(otherId));
        assertThat(postOverride(firstId).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(postOverride(secondId).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> missing = delete("/api/jobs/{id}", UUID.randomUUID());
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> deleted = delete("/api/jobs/{id}", jobId);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(delete("/api/jobs/{id}", jobId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jobs.findById(jobId)).isEmpty();
        assertThat(candidates.findById(firstId)).isEmpty();
        assertThat(candidates.findById(secondId)).isEmpty();
        assertThat(matches.findById(firstId)).isEmpty();
        assertThat(matches.findById(secondId)).isEmpty();
        assertThat(overrides.findByCandidateIdOrderByCreatedAtDesc(firstId)).isEmpty();
        assertThat(overrides.findByCandidateIdOrderByCreatedAtDesc(secondId)).isEmpty();
        assertThat(get("/api/jobs/{id}/rankings", jobId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(delete("/api/candidates/{id}", firstId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(jobs.findById(otherJobId)).isPresent();
        assertThat(candidates.findById(otherId)).isPresent();
        assertThat(matches.findById(otherId)).isPresent();
        assertThat(rankings(otherJobId)).extracting(RankingResponse::id).containsExactly(otherId);
    }
}
