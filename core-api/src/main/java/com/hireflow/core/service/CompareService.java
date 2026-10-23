package com.hireflow.core.service;

import com.hireflow.core.client.CompareCandidate;
import com.hireflow.core.client.CompareRequest;
import com.hireflow.core.client.CompareResponse;
import com.hireflow.core.client.ComparisonEntry;
import com.hireflow.core.client.MatcherClient;
import com.hireflow.core.persistence.JobEntity;
import com.hireflow.core.persistence.JobRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CompareService {

    private static final Logger logger = LoggerFactory.getLogger(CompareService.class);
    private static final int TOP_N = 5;

    private final CandidateTx store;
    private final JobRepository jobs;
    private final MatcherClient matcher;
    private final RemoteCall remoteCall;
    private final ConcurrentHashMap<UUID, Object> locks = new ConcurrentHashMap<>();

    public CompareService(
            CandidateTx store,
            JobRepository jobs,
            MatcherClient matcher,
            RemoteCall remoteCall) {
        this.store = store;
        this.jobs = jobs;
        this.matcher = matcher;
        this.remoteCall = remoteCall;
    }

    public void onBatchComplete(UUID jobId, Set<UUID> batchIds) {
        Object lock = locks.computeIfAbsent(jobId, ignored -> new Object());
        synchronized (lock) {
            try {
                runCompare(jobId, batchIds);
            } catch (RuntimeException exception) {
                logger.error("Compare failed for job {}", jobId, exception);
                store.failInFlight(batchIds, exception.getMessage());
            }
        }
    }

    private void runCompare(UUID jobId, Set<UUID> batchIds) {
        JobEntity job = jobs.findById(jobId).orElseThrow();
        List<ScoredCandidate> scored = store.loadScored(jobId);
        List<UUID> batchScored = scored.stream()
                .map(ScoredCandidate::candidateId)
                .filter(batchIds::contains)
                .toList();
        if (scored.size() < 2) {
            store.markDone(batchScored);
            return;
        }

        List<ScoredCandidate> top = scored.subList(0, Math.min(TOP_N, scored.size()));
        List<UUID> topIds = top.stream().map(ScoredCandidate::candidateId).toList();
        store.clearComparison(jobId);
        store.markComparing(topIds);
        try {
            String description = Digests.normalizeDescription(job.getDescription());
            CompareResponse response = remoteCall.execute(() -> matcher.compare(new CompareRequest(
                    description,
                    top.stream().map(CompareService::toCompareCandidate).toList())));
            requireRankings(topIds, response.rankings());
            store.applyComparison(response.rankings());
            store.markDone(union(topIds, batchScored));
        } catch (RuntimeException exception) {
            logger.warn("Compare request failed for job {}: {}", jobId, exception.getMessage());
            store.markFailed(topIds, exception.getMessage());
            List<UUID> outside = batchScored.stream().filter(id -> !topIds.contains(id)).toList();
            store.markDone(outside);
        }
    }

    private static CompareCandidate toCompareCandidate(ScoredCandidate scored) {
        return new CompareCandidate(
                scored.candidateId(),
                scored.blind().skills(),
                scored.blind().yearsOfExperience(),
                scored.blind().workHistory(),
                scored.blind().education(),
                scored.skillsScore(),
                scored.experienceScore(),
                scored.educationScore(),
                scored.summary());
    }

    private static void requireRankings(List<UUID> expected, List<ComparisonEntry> rankings) {
        Set<UUID> expectedIds = Set.copyOf(expected);
        Set<UUID> actual = new LinkedHashSet<>();
        for (ComparisonEntry entry : rankings) {
            actual.add(entry.candidateId());
        }
        if (!actual.equals(expectedIds)) {
            throw new PermanentException("Comparison did not rank the candidates that were sent");
        }
    }

    private static Set<UUID> union(List<UUID> left, List<UUID> right) {
        Set<UUID> ids = new LinkedHashSet<>(left);
        ids.addAll(right);
        return ids;
    }
}
