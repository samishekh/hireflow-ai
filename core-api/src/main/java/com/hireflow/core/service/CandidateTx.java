package com.hireflow.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireflow.core.client.ScoreResponse;
import com.hireflow.core.domain.CandidateStatus;
import com.hireflow.core.domain.ParsedResume;
import com.hireflow.core.persistence.CandidateEntity;
import com.hireflow.core.persistence.CandidateRepository;
import com.hireflow.core.persistence.JobEntity;
import com.hireflow.core.persistence.JobRepository;
import com.hireflow.core.persistence.MatchResultEntity;
import com.hireflow.core.persistence.MatchResultRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CandidateTx {

    private final JobRepository jobs;
    private final CandidateRepository candidates;
    private final MatchResultRepository matches;
    private final ScoringFormulaService formulas;
    private final ObjectMapper objectMapper;

    public CandidateTx(
            JobRepository jobs,
            CandidateRepository candidates,
            MatchResultRepository matches,
            ScoringFormulaService formulas,
            ObjectMapper objectMapper) {
        this.jobs = jobs;
        this.candidates = candidates;
        this.matches = matches;
        this.formulas = formulas;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<UUID> insertPending(UUID jobId, List<PendingUpload> uploads) {
        if (!jobs.existsById(jobId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        }
        Instant now = Instant.now();
        List<UUID> ids = new ArrayList<>();
        for (int index = 0; index < uploads.size(); index++) {
            PendingUpload upload = uploads.get(index);
            CandidateEntity candidate = new CandidateEntity();
            candidate.setId(UUID.randomUUID());
            candidate.setJobId(jobId);
            candidate.setOriginalFilename(upload.filename());
            candidate.setContentHash(Digests.sha256(upload.bytes()));
            candidate.setStatus(CandidateStatus.pending);
            candidate.setAttemptCount(0);
            candidate.setCreatedAt(now.plusMillis(index));
            candidates.save(candidate);
            ids.add(candidate.getId());
        }
        return ids;
    }

    @Transactional
    public void markParsing(UUID candidateId) {
        CandidateEntity candidate = requireCandidate(candidateId);
        candidate.setStatus(CandidateStatus.parsing);
    }

    @Transactional
    public void markScoring(UUID candidateId) {
        CandidateEntity candidate = requireCandidate(candidateId);
        candidate.setStatus(CandidateStatus.scoring);
    }

    @Transactional
    public void incrementAttempt(UUID candidateId) {
        CandidateEntity candidate = requireCandidate(candidateId);
        candidate.setAttemptCount(candidate.getAttemptCount() + 1);
    }

    @Transactional(readOnly = true)
    public Optional<ParsedCopy> findParsedCopy(UUID candidateId, String contentHash) {
        List<CandidateEntity> copies = candidates.findParsedCopies(contentHash, candidateId);
        if (copies.isEmpty()) {
            return Optional.empty();
        }
        CandidateEntity source = copies.getFirst();
        return Optional.of(new ParsedCopy(source.getRawText(), copyResume(source.getParsedData())));
    }

    @Transactional
    public void saveParsed(UUID candidateId, String rawText, ParsedResume parsed) {
        CandidateEntity candidate = requireCandidate(candidateId);
        candidate.setRawText(rawText);
        candidate.setParsedData(copyResume(parsed));
    }

    @Transactional(readOnly = true)
    public ScoringContext scoringContext(UUID candidateId) {
        CandidateEntity candidate = requireCandidate(candidateId);
        JobEntity job = jobs.findById(candidate.getJobId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        return new ScoringContext(job.getDescriptionHash(), Digests.normalizeDescription(job.getDescription()));
    }

    @Transactional(readOnly = true)
    public Optional<MatchResultEntity> findCachedScore(UUID candidateId, String cacheKey) {
        return matches.findFirstByCacheKeyAndCandidateIdNotOrderByCreatedAtAsc(cacheKey, candidateId);
    }

    @Transactional
    public void copyScore(UUID candidateId, UUID sourceCandidateId, String cacheKey) {
        MatchResultEntity source = matches.findById(sourceCandidateId)
                .orElseThrow(() -> new PermanentException("Cached score disappeared before it could be copied"));
        MatchResultEntity copy = new MatchResultEntity();
        copy.setCandidateId(candidateId);
        copy.setSkillsScore(source.getSkillsScore());
        copy.setExperienceScore(source.getExperienceScore());
        copy.setEducationScore(source.getEducationScore());
        copy.setMatchedSkills(ResumeChecks.copyList(source.getMatchedSkills()));
        copy.setMissingSkills(ResumeChecks.copyList(source.getMissingSkills()));
        copy.setStrengths(ResumeChecks.copyList(source.getStrengths()));
        copy.setConcerns(ResumeChecks.copyList(source.getConcerns()));
        copy.setSummary(source.getSummary());
        copy.setModel(source.getModel());
        copy.setCacheKey(cacheKey);
        copy.setCreatedAt(Instant.now());
        formulas.apply(formulas.lock(), copy);
        matches.save(copy);
    }

    @Transactional
    public void saveScore(UUID candidateId, ScoreResponse score, String cacheKey) {
        MatchResultEntity row = new MatchResultEntity();
        row.setCandidateId(candidateId);
        row.setSkillsScore(score.skillsScore());
        row.setExperienceScore(score.experienceScore());
        row.setEducationScore(score.educationScore());
        row.setMatchedSkills(ResumeChecks.copyList(score.matchedSkills()));
        row.setMissingSkills(ResumeChecks.copyList(score.missingSkills()));
        row.setStrengths(ResumeChecks.copyList(score.strengths()));
        row.setConcerns(ResumeChecks.copyList(score.concerns()));
        row.setSummary(score.summary() == null ? "" : score.summary());
        row.setModel(score.model());
        row.setCacheKey(cacheKey);
        row.setCreatedAt(Instant.now());
        formulas.apply(formulas.lock(), row);
        matches.save(row);
    }

    @Transactional
    public void markFailed(UUID candidateId, String message) {
        markFailed(List.of(candidateId), message);
    }

    @Transactional
    public void markFailed(Collection<UUID> candidateIds, String message) {
        String stored = truncate(message);
        for (CandidateEntity candidate : candidates.findAllById(candidateIds)) {
            candidate.setStatus(CandidateStatus.failed);
            candidate.setErrorMessage(stored);
        }
    }

    @Transactional
    public void markDone(Collection<UUID> candidateIds) {
        if (candidateIds.isEmpty()) {
            return;
        }
        for (CandidateEntity candidate : candidates.findAllById(candidateIds)) {
            candidate.setStatus(CandidateStatus.done);
            candidate.setErrorMessage(null);
        }
    }

    public CandidateEntity requireCandidate(UUID candidateId) {
        return candidates.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found"));
    }

    private ParsedResume copyResume(ParsedResume parsed) {
        try {
            return objectMapper.readValue(objectMapper.writeValueAsBytes(parsed), ParsedResume.class);
        } catch (Exception exception) {
            throw new PermanentException("Could not copy parsed resume", exception);
        }
    }

    private static String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "Pipeline failed";
        }
        return message.length() <= 4000 ? message : message.substring(0, 4000);
    }

    public record PendingUpload(String filename, byte[] bytes) {
    }

    public record ParsedCopy(String rawText, ParsedResume parsed) {
    }

    public record ScoringContext(String descriptionHash, String normalizedDescription) {
    }
}