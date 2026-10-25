package com.hireflow.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hireflow.core.client.MatcherClient;
import com.hireflow.core.client.ParseResumeResponse;
import com.hireflow.core.client.ParserClient;
import com.hireflow.core.client.ScoreRequest;
import com.hireflow.core.client.ScoreResponse;
import com.hireflow.core.domain.BlindCandidate;
import com.hireflow.core.domain.Education;
import com.hireflow.core.domain.ParsedResume;
import com.hireflow.core.domain.WorkExperience;
import com.hireflow.core.persistence.MatchResultEntity;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PipelineServiceTest {

    private static final String FILENAME = "maya-chen.pdf";

    @Mock
    private CandidateTx store;

    @Mock
    private ParserClient parser;

    @Mock
    private MatcherClient matcher;

    @Mock
    private RemoteCall remoteCall;

    @Mock
    private CacheKeyFactory cacheKeys;

    @Mock
    private BatchTracker batches;

    @InjectMocks
    private PipelineService pipeline;

    @Test
    void coldCacheParsesThenScores() {
        UUID candidateId = UUID.randomUUID();
        byte[] pdf = pdf();
        ParsedResume parsed = resume();
        ScoreResponse score = score(candidateId);
        UploadBatch batch = batch(candidateId);
        passThroughRemoteCalls();
        when(store.findParsedCopy(candidateId, Digests.sha256(pdf))).thenReturn(Optional.empty());
        when(parser.parse(FILENAME, pdf)).thenReturn(new ParseResumeResponse("raw text", parsed));
        stubScoring(candidateId, Optional.empty());
        when(matcher.score(any(ScoreRequest.class))).thenReturn(score);

        pipeline.processCandidate(candidateId, FILENAME, pdf, batch);

        InOrder order = Mockito.inOrder(parser, matcher);
        order.verify(parser).parse(FILENAME, pdf);
        ArgumentCaptor<ScoreRequest> scores = ArgumentCaptor.forClass(ScoreRequest.class);
        order.verify(matcher).score(scores.capture());
        ScoreRequest request = scores.getValue();
        assertThat(request.candidateId()).isEqualTo(candidateId);
        assertThat(request.jobDescription()).isEqualTo("senior backend engineer");
        assertThat(request.candidate()).isEqualTo(BlindCandidate.from(parsed));
        verify(store, times(2)).incrementAttempt(candidateId);
        verify(store).saveParsed(candidateId, "raw text", parsed);
        verify(store).saveScore(candidateId, score, "cache-key");
        verify(cacheKeys).cacheKey("description-hash", BlindCandidate.from(parsed));
        verify(store, never()).copyScore(any(), any(), any());
        verify(store, never()).markFailed(any(UUID.class), any());
        verify(batches).finished(batch, candidateId);
    }

    @Test
    void parsedCopyHitSkipsTheParserAndStillScores() {
        UUID candidateId = UUID.randomUUID();
        byte[] pdf = pdf();
        ParsedResume parsed = resume();
        ScoreResponse score = score(candidateId);
        UploadBatch batch = batch(candidateId);
        passThroughRemoteCalls();
        when(store.findParsedCopy(candidateId, Digests.sha256(pdf)))
                .thenReturn(Optional.of(new CandidateTx.ParsedCopy("cached text", parsed)));
        stubScoring(candidateId, Optional.empty());
        when(matcher.score(any(ScoreRequest.class))).thenReturn(score);

        pipeline.processCandidate(candidateId, FILENAME, pdf, batch);

        verify(parser, never()).parse(anyString(), any(byte[].class));
        verify(store).saveParsed(candidateId, "cached text", parsed);
        verify(store, times(1)).incrementAttempt(candidateId);
        verify(matcher).score(any(ScoreRequest.class));
        verify(store).saveScore(candidateId, score, "cache-key");
        verify(batches).finished(batch, candidateId);
    }

    @Test
    void scoreCacheHitCopiesTheScoreAndSkipsTheMatcher() {
        UUID candidateId = UUID.randomUUID();
        UUID sourceCandidateId = UUID.randomUUID();
        byte[] pdf = pdf();
        ParsedResume parsed = resume();
        UploadBatch batch = batch(candidateId);
        MatchResultEntity cached = new MatchResultEntity();
        cached.setCandidateId(sourceCandidateId);
        passThroughRemoteCalls();
        when(store.findParsedCopy(candidateId, Digests.sha256(pdf))).thenReturn(Optional.empty());
        when(parser.parse(FILENAME, pdf)).thenReturn(new ParseResumeResponse("raw text", parsed));
        stubScoring(candidateId, Optional.of(cached));

        pipeline.processCandidate(candidateId, FILENAME, pdf, batch);

        verify(parser).parse(FILENAME, pdf);
        verify(store).copyScore(candidateId, sourceCandidateId, "cache-key");
        verify(matcher, never()).score(any());
        verify(store, never()).saveScore(any(), any(), any());
        verify(store, times(1)).incrementAttempt(candidateId);
        verify(batches).finished(batch, candidateId);
    }

    @Test
    void anyExceptionMarksFailedAndFinishesTheBatch() {
        UUID candidateId = UUID.randomUUID();
        byte[] pdf = pdf();
        UploadBatch batch = batch(candidateId);
        passThroughRemoteCalls();
        when(store.findParsedCopy(candidateId, Digests.sha256(pdf))).thenReturn(Optional.empty());
        when(parser.parse(FILENAME, pdf)).thenThrow(new IllegalStateException("parser exploded"));

        pipeline.processCandidate(candidateId, FILENAME, pdf, batch);

        verify(store).markFailed(candidateId, "parser exploded");
        verify(matcher, never()).score(any());
        verify(store, never()).saveScore(any(), any(), any());
        verify(batches).finished(batch, candidateId);
    }

    private void stubScoring(UUID candidateId, Optional<MatchResultEntity> cachedScore) {
        when(store.scoringContext(candidateId))
                .thenReturn(new CandidateTx.ScoringContext("description-hash", "senior backend engineer"));
        when(cacheKeys.cacheKey(any(), any())).thenReturn("cache-key");
        when(store.findCachedScore(candidateId, "cache-key")).thenReturn(cachedScore);
    }

    @SuppressWarnings("unchecked")
    private void passThroughRemoteCalls() {
        doAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(0)).get())
                .when(remoteCall)
                .execute(any());
    }

    private static UploadBatch batch(UUID candidateId) {
        return new UploadBatch(UUID.randomUUID(), Set.of(candidateId));
    }

    private static byte[] pdf() {
        return "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
    }

    private static ParsedResume resume() {
        return new ParsedResume(
                "Maya Chen",
                "maya@example.com",
                "555-0199",
                List.of("Java", "PostgreSQL"),
                6.0,
                List.of(new WorkExperience("Northwind", "Engineer", "2019-2024", "Built APIs")),
                List.of(new Education("State University", "BS", "Computer Science", "2015-2019")));
    }

    private static ScoreResponse score(UUID candidateId) {
        return new ScoreResponse(
                candidateId,
                80.0,
                70.0,
                60.0,
                75.0,
                List.of("Java"),
                List.of("Kubernetes"),
                List.of("API design"),
                List.of("No cloud depth"),
                "Strong backend background",
                "v1",
                "claude");
    }
}
