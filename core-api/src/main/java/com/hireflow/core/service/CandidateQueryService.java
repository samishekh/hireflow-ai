package com.hireflow.core.service;

import com.hireflow.core.domain.CandidateStatus;
import com.hireflow.core.persistence.CandidateEntity;
import com.hireflow.core.persistence.CandidateRepository;
import com.hireflow.core.persistence.MatchResultEntity;
import com.hireflow.core.persistence.MatchResultRepository;
import com.hireflow.core.persistence.ScoreOverrideEntity;
import com.hireflow.core.persistence.ScoreOverrideRepository;
import com.hireflow.core.web.CandidateResponse;
import com.hireflow.core.web.OverrideRequest;
import com.hireflow.core.web.OverrideResponse;
import com.hireflow.core.web.RankingResponse;
import com.hireflow.core.web.ScoreBreakdown;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CandidateQueryService {

    private final JobService jobs;
    private final CandidateRepository candidates;
    private final MatchResultRepository matches;
    private final ScoreOverrideRepository overrides;

    public CandidateQueryService(
            JobService jobs,
            CandidateRepository candidates,
            MatchResultRepository matches,
            ScoreOverrideRepository overrides) {
        this.jobs = jobs;
        this.candidates = candidates;
        this.matches = matches;
        this.overrides = overrides;
    }

    @Transactional(readOnly = true)
    public List<RankingResponse> rankings(UUID jobId) {
        jobs.require(jobId);
        List<CandidateEntity> rows = candidates.findByJobIdOrderByCreatedAtAsc(jobId);
        Map<UUID, MatchResultEntity> scores = indexScores(rows);
        Map<UUID, ScoreOverrideEntity> latest = latestOverrides(rows);
        List<CandidateEntity> ordered = new ArrayList<>(rows);
        ordered.sort(Comparator
                .comparing((CandidateEntity candidate) -> {
                    MatchResultEntity score = scores.get(candidate.getId());
                    return score == null ? null : score.getOverallScore();
                }, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(CandidateEntity::getCreatedAt));
        return ordered.stream()
                .map(candidate -> new RankingResponse(
                        candidate.getId(),
                        candidate.getOriginalFilename(),
                        candidate.getStatus(),
                        candidate.getErrorMessage(),
                        breakdown(scores.get(candidate.getId())),
                        overrideResponse(latest.get(candidate.getId()))))
                .toList();
    }

    @Transactional(readOnly = true)
    public CandidateResponse get(UUID candidateId) {
        CandidateEntity candidate = candidates.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found"));
        MatchResultEntity score = matches.findById(candidateId).orElse(null);
        return new CandidateResponse(
                candidate.getId(),
                candidate.getJobId(),
                candidate.getOriginalFilename(),
                candidate.getStatus(),
                candidate.getErrorMessage(),
                candidate.getRawText(),
                candidate.getParsedData(),
                breakdown(score));
    }

    @Transactional
    public OverrideResponse override(UUID candidateId, OverrideRequest request) {
        CandidateEntity candidate = candidates.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found"));
        if (candidate.getStatus() != CandidateStatus.done) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Override is available after the candidate is done");
        }
        ScoreOverrideEntity row = new ScoreOverrideEntity();
        row.setId(UUID.randomUUID());
        row.setCandidateId(candidateId);
        row.setDecision(request.decision());
        row.setNote(request.note().trim());
        row.setDecidedBy(request.decidedBy().trim());
        row.setCreatedAt(Instant.now());
        return overrideResponse(overrides.save(row));
    }

    private Map<UUID, MatchResultEntity> indexScores(List<CandidateEntity> rows) {
        List<UUID> ids = rows.stream().map(CandidateEntity::getId).toList();
        Map<UUID, MatchResultEntity> scores = new HashMap<>();
        if (ids.isEmpty()) {
            return scores;
        }
        for (MatchResultEntity match : matches.findByCandidateIdIn(ids)) {
            scores.put(match.getCandidateId(), match);
        }
        return scores;
    }

    private Map<UUID, ScoreOverrideEntity> latestOverrides(List<CandidateEntity> rows) {
        List<UUID> ids = rows.stream().map(CandidateEntity::getId).toList();
        Map<UUID, ScoreOverrideEntity> latest = new HashMap<>();
        if (ids.isEmpty()) {
            return latest;
        }
        for (ScoreOverrideEntity override : overrides.findByCandidateIdInOrderByCreatedAtDesc(ids)) {
            latest.putIfAbsent(override.getCandidateId(), override);
        }
        return latest;
    }

    private static ScoreBreakdown breakdown(MatchResultEntity score) {
        if (score == null) {
            return null;
        }
        return new ScoreBreakdown(
                score.getSkillsScore(),
                score.getExperienceScore(),
                score.getEducationScore(),
                score.getOverallScore(),
                score.getMatchedSkills(),
                score.getMissingSkills(),
                score.getStrengths(),
                score.getConcerns(),
                score.getSummary(),
                score.getFormulaVersion(),
                score.getModel());
    }

    private static OverrideResponse overrideResponse(ScoreOverrideEntity override) {
        if (override == null) {
            return null;
        }
        return new OverrideResponse(
                override.getId(),
                override.getCandidateId(),
                override.getDecision(),
                override.getNote(),
                override.getDecidedBy(),
                override.getCreatedAt());
    }
}
