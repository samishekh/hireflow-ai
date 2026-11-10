package com.hireflow.core.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.hireflow.core.domain.CandidateStatus;
import com.hireflow.core.domain.OverrideDecision;
import com.hireflow.core.persistence.ScoreOverrideEntity;
import com.hireflow.core.service.ScoreMath;
import com.hireflow.core.web.CandidateResponse;
import com.hireflow.core.web.OverrideResponse;
import com.hireflow.core.web.RankingResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class PipelineIntegrationTest extends PipelineTestSupport {

    @Test
    void singlePdfReachesDone() {
        String skill = "single-upload-skill";
        stubParseAndScore(skill, 80, 70, 60, 74.5);

        UUID jobId = createJob("Single resume");
        UUID candidateId = upload(jobId, pdf("one.pdf", skill)).getFirst();

        CandidateResponse candidate = awaitTerminal(candidateId);
        assertDone(candidate);
        assertThat(candidate.score().skillsScore()).isEqualTo(80.0);
        assertThat(candidate.score().experienceScore()).isEqualTo(70.0);
        assertThat(candidate.score().educationScore()).isEqualTo(60.0);
        assertThat(candidate.score().overallScore()).isEqualTo(v1(80, 70, 60));
        assertThat(candidate.score().formulaVersion()).isEqualTo("v1");
        assertThat(candidate.score().model()).isEqualTo("mock");
        assertThat(requestCount("/parse-resume")).isEqualTo(1);
        assertThat(requestCount("/score")).isEqualTo(1);
    }

    @Test
    void batchOfTwoRanksByOverallScore() {
        String javaSkill = "batch-java-skill";
        String pythonSkill = "batch-python-skill";
        stubParseAndScore(javaSkill, 92, 88, 80, 90);
        stubParseAndScore(pythonSkill, 40, 50, 45, 55);

        UUID jobId = createJob("Two resumes");
        List<UUID> ids = upload(
                jobId,
                pdf("python.pdf", pythonSkill),
                pdf("java.pdf", javaSkill));
        assertThat(ids).hasSize(2);
        UUID pythonId = ids.get(0);
        UUID javaId = ids.get(1);

        assertDone(awaitTerminal(pythonId));
        assertDone(awaitTerminal(javaId));
        assertThat(requestCount("/parse-resume")).isEqualTo(2);
        assertThat(requestCount("/score")).isEqualTo(2);

        List<RankingResponse> scored = rankings(jobId);
        assertThat(scored).extracting(RankingResponse::originalFilename).containsExactly("java.pdf", "python.pdf");
        assertThat(scored).extracting(row -> row.score().overallScore()).containsExactly(v1(92, 88, 80), v1(40, 50, 45));
        assertThat(scored).allSatisfy(row -> assertThat(row.status()).isEqualTo(CandidateStatus.done));
    }

    @Test
    void reuploadCopiesParseAndScoreWithoutCallingThemAgain() {
        String skill = "cache-reuse-skill";
        stubParseAndScore(skill, 80, 70, 60, 74.5);

        UUID jobId = createJob("Cache reuse");
        byte[] bytes = pdf("first.pdf", skill).bytes();
        UUID firstId = upload(jobId, new NamedPdf("first.pdf", bytes)).getFirst();
        CandidateResponse first = awaitTerminal(firstId);
        assertDone(first);
        int parses = requestCount("/parse-resume");
        int scores = requestCount("/score");
        assertThat(parses).isEqualTo(1);
        assertThat(scores).isEqualTo(1);

        UUID secondId = upload(jobId, new NamedPdf("second.pdf", bytes)).getFirst();
        CandidateResponse second = awaitTerminal(secondId);
        assertDone(second);
        assertThat(secondId).isNotEqualTo(firstId);
        assertThat(requestCount("/parse-resume")).isEqualTo(parses);
        assertThat(requestCount("/score")).isEqualTo(scores);
        assertSameScore(second.score(), first.score());
        assertSameScore(getCandidate(firstId).score(), first.score());
    }

    @Test
    void parserHttp400FailsCandidateWithoutScoring() {
        WIRE_MOCK.stubFor(post(urlEqualTo("/parse-resume"))
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"bad pdf\"}")));

        UUID jobId = createJob("Parser failure");
        UUID candidateId = upload(jobId, pdf("broken.pdf", "parser-failure-bytes")).getFirst();

        CandidateResponse candidate = awaitTerminal(candidateId);
        assertThat(candidate.status()).isEqualTo(CandidateStatus.failed);
        assertThat(candidate.errorMessage()).isEqualTo("HTTP 400 from resume parser");
        assertThat(candidate.score()).isNull();
        assertThat(requestCount("/parse-resume")).isEqualTo(1);
        assertThat(requestCount("/score")).isZero();
    }

    @Test
    void overrideBeforeDoneConflictsAndAfterDonePreservesScore() {
        String skill = "override-skill";
        ParseGate.hold(parseJson(skill));
        WIRE_MOCK.stubFor(post(urlEqualTo("/parse-resume"))
                .withRequestBody(containing(skill))
                .willReturn(aResponse().withTransformers(HoldParseTransformer.NAME)));
        WIRE_MOCK.stubFor(post(urlEqualTo("/score"))
                .withRequestBody(containing(skill))
                .willReturn(okJson(scoreJson(80, 70, 60, 74.5))));

        UUID jobId = createJob("Override");
        UUID candidateId = upload(jobId, pdf("override.pdf", skill)).getFirst();
        try {
            ResponseEntity<String> tooEarly = postOverride(candidateId);
            assertThat(tooEarly.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(overrides.findByCandidateIdOrderByCreatedAtDesc(candidateId)).isEmpty();
        } finally {
            ParseGate.release();
        }

        CandidateResponse done = awaitTerminal(candidateId);
        assertDone(done);
        double overallBefore = done.score().overallScore();

        ResponseEntity<String> created = postOverride(candidateId);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        OverrideResponse body = read(created.getBody(), OverrideResponse.class);
        assertThat(body.candidateId()).isEqualTo(candidateId);
        assertThat(body.decision()).isEqualTo(OverrideDecision.accepted);
        assertThat(body.note()).isEqualTo("panel agrees");
        assertThat(body.decidedBy()).isEqualTo("sami");

        ScoreOverrideEntity row = overrides.findByCandidateIdOrderByCreatedAtDesc(candidateId).getFirst();
        assertThat(overrides.findByCandidateIdOrderByCreatedAtDesc(candidateId)).hasSize(1);
        assertThat(row.getId()).isEqualTo(body.id());
        assertThat(row.getCandidateId()).isEqualTo(candidateId);
        assertThat(row.getDecision()).isEqualTo(OverrideDecision.accepted);
        assertThat(row.getNote()).isEqualTo("panel agrees");
        assertThat(row.getDecidedBy()).isEqualTo("sami");
        assertThat(getCandidate(candidateId).score().overallScore()).isEqualTo(overallBefore);
    }

    private static double v1(double skills, double experience, double education) {
        return ScoreMath.overall(
                ScoreMath.V1_SKILLS,
                ScoreMath.V1_EXPERIENCE,
                ScoreMath.V1_EDUCATION,
                skills,
                experience,
                education);
    }
}
