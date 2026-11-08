package com.hireflow.core.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.hireflow.core.client.ParseResumeResponse;
import com.hireflow.core.client.ScoreResponse;
import com.hireflow.core.domain.CandidateStatus;
import com.hireflow.core.domain.Education;
import com.hireflow.core.domain.ParsedResume;
import com.hireflow.core.domain.WorkExperience;
import com.hireflow.core.persistence.ScoreOverrideRepository;
import com.hireflow.core.web.CandidateResponse;
import com.hireflow.core.web.JobResponse;
import com.hireflow.core.web.RankingResponse;
import com.hireflow.core.web.ScoreBreakdown;
import com.hireflow.core.web.UploadResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
abstract class PipelineTestSupport {

    private static final TypeReference<List<RankingResponse>> RANKINGS = new TypeReference<>() {
    };

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    static final WireMockServer WIRE_MOCK = startWireMock();

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    ScoreOverrideRepository overrides;

    @DynamicPropertySource
    static void hireflowClients(DynamicPropertyRegistry registry) {
        registry.add("hireflow.parser-base-url", WIRE_MOCK::baseUrl);
        registry.add("hireflow.matcher-base-url", WIRE_MOCK::baseUrl);
        registry.add("hireflow.retry.initial-delay", () -> "1ms");
    }

    @BeforeEach
    void resetDownstream() {
        ParseGate.release();
        WIRE_MOCK.resetAll();
    }

    @AfterEach
    void releaseParser() {
        ParseGate.release();
    }

    @AfterAll
    static void stopWireMock() {
        WIRE_MOCK.stop();
    }

    void stubParseAndScore(String skill, double skills, double experience, double education, double overall) {
        WIRE_MOCK.stubFor(post(urlEqualTo("/parse-resume"))
                .withRequestBody(containing(skill))
                .willReturn(okJson(parseJson(skill))));
        WIRE_MOCK.stubFor(post(urlEqualTo("/score"))
                .withRequestBody(containing(skill))
                .willReturn(okJson(scoreJson(skills, experience, education, overall))));
    }

    UUID createJob(String title) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"title":"%s","description":"Backend engineer who writes services and reviews resumes."}
                """.formatted(title);
        ResponseEntity<String> response = rest.postForEntity(
                "/api/jobs",
                new HttpEntity<>(body, headers),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return read(response.getBody(), JobResponse.class).id();
    }

    List<UUID> upload(UUID jobId, NamedPdf... files) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        for (NamedPdf file : files) {
            parts.add("files", file.resource());
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> response = rest.postForEntity(
                "/api/jobs/{id}/candidates",
                new HttpEntity<>(parts, headers),
                String.class,
                jobId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        return read(response.getBody(), UploadResponse.class).candidateIds();
    }

    ResponseEntity<String> postOverride(UUID candidateId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"decision":"accepted","note":"panel agrees","decided_by":"sami"}
                """;
        return rest.postForEntity(
                "/api/candidates/{id}/override",
                new HttpEntity<>(body, headers),
                String.class,
                candidateId);
    }

    CandidateResponse awaitTerminal(UUID candidateId) {
        return await()
                .atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(50))
                .until(() -> {
                    CandidateResponse candidate = getCandidate(candidateId);
                    CandidateStatus status = candidate.status();
                    if (status == CandidateStatus.done || status == CandidateStatus.failed) {
                        return candidate;
                    }
                    return null;
                }, Objects::nonNull);
    }

    CandidateResponse getCandidate(UUID candidateId) {
        ResponseEntity<String> response = rest.getForEntity("/api/candidates/{id}", String.class, candidateId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return read(response.getBody(), CandidateResponse.class);
    }

    List<RankingResponse> rankings(UUID jobId) {
        ResponseEntity<String> response = rest.getForEntity("/api/jobs/{id}/rankings", String.class, jobId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return read(response.getBody(), RANKINGS);
    }

    String parseJson(String skill) {
        ParsedResume parsed = new ParsedResume(
                "Hidden Name",
                "hidden@example.com",
                "555-0100",
                List.of(skill),
                6.5,
                List.of(new WorkExperience("Acme", "Engineer", "2020 - 2024", "Built APIs")),
                List.of(new Education("State University", "B.S.", "Computer Science", "2016 - 2020")));
        return write(new ParseResumeResponse("text for " + skill, parsed));
    }

    String scoreJson(double skills, double experience, double education, double overall) {
        return write(new ScoreResponse(
                UUID.fromString("00000000-0000-0000-0000-000000000000"),
                skills,
                experience,
                education,
                overall,
                List.of("matched"),
                List.of("missing"),
                List.of("strength"),
                List.of("concern"),
                "solid fit",
                "v1",
                "mock"));
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static void assertDone(CandidateResponse candidate) {
        assertThat(candidate)
                .extracting(CandidateResponse::status, CandidateResponse::errorMessage)
                .containsExactly(CandidateStatus.done, null);
    }

    static void assertSameScore(ScoreBreakdown actual, ScoreBreakdown expected) {
        assertThat(actual.skillsScore()).isEqualTo(expected.skillsScore());
        assertThat(actual.experienceScore()).isEqualTo(expected.experienceScore());
        assertThat(actual.educationScore()).isEqualTo(expected.educationScore());
        assertThat(actual.overallScore()).isEqualTo(expected.overallScore());
        assertThat(actual.formulaVersion()).isEqualTo(expected.formulaVersion());
        assertThat(actual.model()).isEqualTo(expected.model());
    }

    static int requestCount(String path) {
        return WIRE_MOCK.findAll(postRequestedFor(urlEqualTo(path))).size();
    }

    private static WireMockServer startWireMock() {
        WireMockServer server = new WireMockServer(wireMockConfig()
                .dynamicPort()
                .http2PlainDisabled(true)
                .extensions(new HoldParseTransformer()));
        server.start();
        return server;
    }

    record NamedPdf(String filename, byte[] bytes) {

        private ByteArrayResource resource() {
            return new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
        }
    }

    private static byte[] pdfBytes(String marker) {
        return ("%PDF-1.4\n% " + marker + "\n").getBytes(StandardCharsets.US_ASCII);
    }

    static NamedPdf pdf(String filename, String marker) {
        return new NamedPdf(filename, pdfBytes(marker));
    }
}
