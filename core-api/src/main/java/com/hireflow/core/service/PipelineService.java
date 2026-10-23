package com.hireflow.core.service;

import com.hireflow.core.client.MatcherClient;
import com.hireflow.core.client.ParserClient;
import com.hireflow.core.client.ParseResumeResponse;
import com.hireflow.core.client.ScoreRequest;
import com.hireflow.core.client.ScoreResponse;
import com.hireflow.core.domain.BlindCandidate;
import com.hireflow.core.domain.ParsedResume;
import com.hireflow.core.persistence.MatchResultEntity;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class PipelineService {

    private static final Logger logger = LoggerFactory.getLogger(PipelineService.class);

    private final CandidateTx store;
    private final ParserClient parser;
    private final MatcherClient matcher;
    private final RemoteCall remoteCall;
    private final CacheKeyFactory cacheKeys;
    private final BatchTracker batches;
    private final ConcurrentHashMap<String, Object> scoreLocks = new ConcurrentHashMap<>();

    public PipelineService(
            CandidateTx store,
            ParserClient parser,
            MatcherClient matcher,
            RemoteCall remoteCall,
            CacheKeyFactory cacheKeys,
            BatchTracker batches) {
        this.store = store;
        this.parser = parser;
        this.matcher = matcher;
        this.remoteCall = remoteCall;
        this.cacheKeys = cacheKeys;
        this.batches = batches;
    }

    @Async("taskExecutor")
    public void processCandidate(UUID candidateId, String filename, byte[] pdf, UploadBatch batch) {
        try {
            parseAndScore(candidateId, filename, pdf);
        } catch (Throwable exception) {
            logger.warn("Candidate {} failed: {}", candidateId, exception.getMessage());
            store.markFailed(candidateId, exception.getMessage());
        } finally {
            batches.finished(batch, candidateId);
        }
    }

    private void parseAndScore(UUID candidateId, String filename, byte[] pdf) {
        store.markParsing(candidateId);
        String contentHash = Digests.sha256(pdf);
        Optional<CandidateTx.ParsedCopy> cachedParse = store.findParsedCopy(candidateId, contentHash);
        ParsedResume parsed;
        if (cachedParse.isPresent()) {
            logger.info("Reusing parsed resume for candidate {}", candidateId);
            parsed = cachedParse.get().parsed();
            store.saveParsed(candidateId, cachedParse.get().rawText(), parsed);
        } else {
            ParseResumeResponse response = remoteCall.execute(() -> {
                store.incrementAttempt(candidateId);
                return parser.parse(filename, pdf);
            });
            parsed = response.parsedData();
            store.saveParsed(candidateId, response.rawText(), parsed);
        }

        BlindCandidate blind = ResumeChecks.blind(parsed);
        CandidateTx.ScoringContext context = store.scoringContext(candidateId);
        String cacheKey = cacheKeys.cacheKey(context.descriptionHash(), blind);
        store.markScoring(candidateId);
        Object scoreLock = scoreLocks.computeIfAbsent(cacheKey, ignored -> new Object());
        synchronized (scoreLock) {
            Optional<MatchResultEntity> cachedScore = store.findCachedScore(candidateId, cacheKey);
            if (cachedScore.isPresent()) {
                logger.info("Reusing score for candidate {}", candidateId);
                store.copyScore(candidateId, cachedScore.get().getCandidateId(), cacheKey);
                return;
            }

            ScoreResponse score = remoteCall.execute(() -> {
                store.incrementAttempt(candidateId);
                return matcher.score(new ScoreRequest(candidateId, context.normalizedDescription(), blind));
            });
            store.saveScore(candidateId, score, cacheKey);
        }
    }
}
