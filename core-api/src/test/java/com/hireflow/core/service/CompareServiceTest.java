package com.hireflow.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hireflow.core.client.CompareCandidate;
import com.hireflow.core.client.CompareRequest;
import com.hireflow.core.client.CompareResponse;
import com.hireflow.core.client.ComparisonEntry;
import com.hireflow.core.client.MatcherClient;
import com.hireflow.core.domain.BlindCandidate;
import com.hireflow.core.domain.Education;
import com.hireflow.core.domain.WorkExperience;
import com.hireflow.core.persistence.JobEntity;
import com.hireflow.core.persistence.JobRepository;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
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
class CompareServiceTest {

    private static final String RANKING_MISMATCH = "Comparison did not rank the candidates that were sent";

    @Mock
    private CandidateTx store;

    @Mock
    private JobRepository jobs;

    @Mock
    private MatcherClient matcher;

    @Mock
    private RemoteCall remoteCall;

    @InjectMocks
    private CompareService compareService;

    @Test
    void oneScoredCandidateIsMarkedDoneWithoutCompare() {
        UUID jobId = UUID.randomUUID();
        ScoredCandidate only = scoredCandidate(88, "Java");
        stubJob(jobId);
        when(store.loadScored(jobId)).thenReturn(List.of(only));

        compareService.onBatchComplete(jobId, Set.of(only.candidateId()));

        verify(store).markDone(List.of(only.candidateId()));
        verify(store, never()).markFailed(any(UUID.class), nullable(String.class));
        verify(store, never()).markFailed(anyCollection(), nullable(String.class));
        verify(store, never()).failInFlight(anyCollection(), nullable(String.class));
        verifyNoInteractions(matcher, remoteCall);
    }

    @Test
    void sendsOnlyTheTopFiveBlindProfiles() {
        UUID jobId = UUID.randomUUID();
        List<ScoredCandidate> scored = sixScored();
        List<UUID> topIds = ids(scored.subList(0, 5));
        // Id order does not matter: requireRankings compares the sent ids as a set.
        List<ComparisonEntry> rankings = new ArrayList<>();
        for (int index = topIds.size() - 1; index >= 0; index--) {
            rankings.add(new ComparisonEntry(topIds.get(index), topIds.size() - index, "fit"));
        }
        stubJob(jobId);
        when(store.loadScored(jobId)).thenReturn(scored);
        passThroughRemoteCalls();
        when(matcher.compare(any(CompareRequest.class))).thenReturn(new CompareResponse(rankings, "claude"));

        compareService.onBatchComplete(jobId, batchIds(scored));

        ArgumentCaptor<CompareRequest> captor = ArgumentCaptor.forClass(CompareRequest.class);
        InOrder order = Mockito.inOrder(store, matcher);
        order.verify(store).clearComparison(jobId);
        order.verify(store).markComparing(topIds);
        order.verify(matcher).compare(captor.capture());
        order.verify(store).applyComparison(rankings);
        order.verify(store).markDone(Set.copyOf(ids(scored)));

        CompareRequest request = captor.getValue();
        assertThat(request.jobDescription()).isEqualTo("senior backend engineer");
        assertThat(request.candidates()).hasSize(5);
        assertThat(request.candidates())
                .extracting(candidate -> candidate.skills().getFirst())
                .containsExactly("Java", "Go", "Python", "Rust", "Kotlin");
        for (int index = 0; index < 5; index++) {
            assertBlindPayload(request.candidates().get(index), scored.get(index));
        }
        assertThat(CompareCandidate.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("candidateName", "email", "phone");
        verify(store, never()).markFailed(anyCollection(), nullable(String.class));
    }

    @Test
    void rejectsRankingsWhoseIdsAreNotTheSentSet() {
        UUID jobId = UUID.randomUUID();
        List<ScoredCandidate> scored = sixScored();
        List<UUID> topIds = ids(scored.subList(0, 5));
        UUID excluded = scored.get(5).candidateId();
        List<ComparisonEntry> rankings = new ArrayList<>();
        for (int index = 0; index < topIds.size() - 1; index++) {
            rankings.add(new ComparisonEntry(topIds.get(index), index + 1, "fit"));
        }
        rankings.add(new ComparisonEntry(excluded, topIds.size(), "not sent"));
        stubJob(jobId);
        when(store.loadScored(jobId)).thenReturn(scored);
        passThroughRemoteCalls();
        when(matcher.compare(any(CompareRequest.class))).thenReturn(new CompareResponse(rankings, "claude"));

        compareService.onBatchComplete(jobId, batchIds(scored));

        ArgumentCaptor<CompareRequest> captor = ArgumentCaptor.forClass(CompareRequest.class);
        verify(matcher).compare(captor.capture());
        assertThat(captor.getValue().candidates())
                .extracting(CompareCandidate::candidateId)
                .containsExactlyElementsOf(topIds);
        verify(store).markFailed(topIds, RANKING_MISMATCH);
        verify(store).markDone(List.of(excluded));
        verify(store, never()).applyComparison(any());
        verify(store, never()).failInFlight(anyCollection(), nullable(String.class));
    }

    @Test
    void matcherFailureMarksTheTopFailedAndTheRestOfTheBatchDone() {
        UUID jobId = UUID.randomUUID();
        List<ScoredCandidate> scored = sixScored();
        List<UUID> topIds = ids(scored.subList(0, 5));
        UUID excluded = scored.get(5).candidateId();
        stubJob(jobId);
        when(store.loadScored(jobId)).thenReturn(scored);
        passThroughRemoteCalls();
        when(matcher.compare(any(CompareRequest.class)))
                .thenThrow(new PermanentException("HTTP 400 from job matcher"));

        compareService.onBatchComplete(jobId, batchIds(scored));

        verify(store).markFailed(topIds, "HTTP 400 from job matcher");
        verify(store).markDone(List.of(excluded));
        verify(store, never()).applyComparison(any());
        verify(store, never()).failInFlight(anyCollection(), nullable(String.class));
    }

    private void stubJob(UUID jobId) {
        JobEntity job = new JobEntity();
        job.setId(jobId);
        job.setDescription("  Senior   Backend Engineer ");
        when(jobs.findById(jobId)).thenReturn(Optional.of(job));
    }

    @SuppressWarnings("unchecked")
    private void passThroughRemoteCalls() {
        doAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(0)).get())
                .when(remoteCall)
                .execute(any());
    }

    private static void assertBlindPayload(CompareCandidate sent, ScoredCandidate source) {
        assertThat(sent.candidateId()).isEqualTo(source.candidateId());
        assertThat(sent.skills()).isEqualTo(source.blind().skills());
        assertThat(sent.yearsOfExperience()).isEqualTo(source.blind().yearsOfExperience());
        assertThat(sent.workHistory()).isEqualTo(source.blind().workHistory());
        assertThat(sent.education()).isEqualTo(source.blind().education());
        assertThat(sent.skillsScore()).isEqualTo(source.skillsScore());
        assertThat(sent.experienceScore()).isEqualTo(source.experienceScore());
        assertThat(sent.educationScore()).isEqualTo(source.educationScore());
        assertThat(sent.summary()).isEqualTo(source.summary());
    }

    private static List<ScoredCandidate> sixScored() {
        String[] skills = {"Java", "Go", "Python", "Rust", "Kotlin", "Ruby"};
        List<ScoredCandidate> scored = new ArrayList<>();
        for (int index = 0; index < skills.length; index++) {
            scored.add(scoredCandidate(90 - (index * 10.0), skills[index]));
        }
        return scored;
    }

    private static ScoredCandidate scoredCandidate(double overall, String skill) {
        BlindCandidate blind = new BlindCandidate(
                List.of(skill),
                overall / 10,
                List.of(new WorkExperience(skill + " Labs", "Engineer", "2020-2024", "Built " + skill + " services")),
                List.of(new Education(skill + " University", "BS", "Computer Science", "2016-2020")));
        return new ScoredCandidate(
                UUID.randomUUID(),
                Instant.EPOCH,
                overall,
                blind,
                overall,
                overall - 5,
                overall - 10,
                skill + " is a fit");
    }

    private static List<UUID> ids(List<ScoredCandidate> scored) {
        return scored.stream().map(ScoredCandidate::candidateId).toList();
    }

    private static Set<UUID> batchIds(List<ScoredCandidate> scored) {
        return Set.copyOf(ids(scored));
    }
}
