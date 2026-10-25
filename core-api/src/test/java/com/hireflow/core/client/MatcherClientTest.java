package com.hireflow.core.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.hireflow.core.config.HireflowProperties;
import com.hireflow.core.domain.BlindCandidate;
import com.hireflow.core.domain.Education;
import com.hireflow.core.domain.WorkExperience;
import com.hireflow.core.service.PermanentException;
import com.hireflow.core.service.RetryableException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

class MatcherClientTest {

    private static final UUID CANDIDATE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private WireMockServer wireMock;
    private MatcherClient client;

    @BeforeEach
    void setUp() {
        wireMock = TestRestClients.wireMock();
        client = new MatcherClient(TestRestClients.snakeCase(), properties(wireMock.baseUrl()));
    }

    @AfterEach
    void tearDown() {
        if (wireMock.isRunning()) {
            wireMock.stop();
        }
    }

    @Test
    void scorePostsBlindJsonAndBindsSnakeCaseResponse() {
        wireMock.stubFor(post(urlEqualTo("/score")).willReturn(okJson(scoreBody(true, true))));

        ScoreResponse response = client.score(scoreRequest());

        String body = postedBody("/score");
        assertThat(body)
                .doesNotContain("candidate_name")
                .doesNotContain("email")
                .doesNotContain("phone")
                .contains("\"candidate_id\"")
                .contains("\"job_description\"")
                .contains("\"years_of_experience\"")
                .contains("\"field_of_study\"")
                .contains("\"work_history\"");
        assertThat(response.candidateId()).isEqualTo(CANDIDATE_ID);
        assertThat(response.skillsScore()).isEqualTo(80.0);
        assertThat(response.experienceScore()).isEqualTo(70.0);
        assertThat(response.educationScore()).isEqualTo(60.0);
        assertThat(response.overallScore()).isEqualTo(74.5);
        assertThat(response.matchedSkills()).containsExactly("Java");
        assertThat(response.missingSkills()).containsExactly("Go");
        assertThat(response.strengths()).containsExactly("backend services");
        assertThat(response.concerns()).containsExactly("short tenure");
        assertThat(response.summary()).isEqualTo("solid backend fit");
        assertThat(response.formulaVersion()).isEqualTo("v1");
        assertThat(response.model()).isEqualTo("mock");
    }

    @Test
    void comparePostsJsonAndBindsRankings() {
        String json = """
                {
                  "rankings": [
                    {
                      "candidate_id": "22222222-2222-2222-2222-222222222222",
                      "rank": 1,
                      "reason": "stronger skills"
                    },
                    {
                      "candidate_id": "11111111-1111-1111-1111-111111111111",
                      "rank": 2,
                      "reason": "less experience"
                    }
                  ],
                  "model": "mock"
                }
                """;
        wireMock.stubFor(post(urlEqualTo("/compare")).willReturn(okJson(json)));

        CompareResponse response = client.compare(compareRequest());

        String body = postedBody("/compare");
        assertThat(body)
                .doesNotContain("candidate_name")
                .doesNotContain("email")
                .doesNotContain("phone")
                .contains("\"job_description\"")
                .contains("\"candidate_id\"")
                .contains("\"years_of_experience\"")
                .contains("\"skills_score\"")
                .contains("\"experience_score\"")
                .contains("\"education_score\"")
                .contains("\"field_of_study\"");
        assertThat(response.model()).isEqualTo("mock");
        assertThat(response.rankings()).containsExactly(
                new ComparisonEntry(OTHER_ID, 1, "stronger skills"),
                new ComparisonEntry(CANDIDATE_ID, 2, "less experience"));
    }

    @Test
    void missingFormulaVersionIsPermanent() {
        wireMock.stubFor(post(urlEqualTo("/score")).willReturn(okJson(scoreBody(false, true))));

        assertThatThrownBy(() -> client.score(scoreRequest()))
                .isInstanceOf(PermanentException.class)
                .hasMessage("Job matcher returned an empty score")
                .hasNoCause();
    }

    @Test
    void missingModelIsPermanent() {
        wireMock.stubFor(post(urlEqualTo("/score")).willReturn(okJson(scoreBody(true, false))));

        assertThatThrownBy(() -> client.score(scoreRequest()))
                .isInstanceOf(PermanentException.class)
                .hasMessage("Job matcher returned an empty score")
                .hasNoCause();
    }

    @Test
    void emptyRankingsIsPermanent() {
        wireMock.stubFor(post(urlEqualTo("/compare")).willReturn(okJson("""
                {"rankings": null, "model": "mock"}
                """)));

        assertThatThrownBy(() -> client.compare(compareRequest()))
                .isInstanceOf(PermanentException.class)
                .hasMessage("Job matcher returned an empty comparison")
                .hasNoCause();
    }

    @Test
    void clientErrorsArePermanent() {
        wireMock.stubFor(post(urlEqualTo("/score")).willReturn(aResponse().withStatus(400)));
        wireMock.stubFor(post(urlEqualTo("/compare")).willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> client.score(scoreRequest()))
                .isInstanceOf(PermanentException.class)
                .hasMessage("HTTP 400 from job matcher")
                .hasCauseInstanceOf(RestClientResponseException.class);
        assertThatThrownBy(() -> client.compare(compareRequest()))
                .isInstanceOf(PermanentException.class)
                .hasMessage("HTTP 429 from job matcher")
                .hasCauseInstanceOf(RestClientResponseException.class);
    }

    @Test
    void serverErrorsAreRetryable() {
        wireMock.stubFor(post(urlEqualTo("/score")).willReturn(aResponse().withStatus(503)));
        wireMock.stubFor(post(urlEqualTo("/compare")).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.score(scoreRequest()))
                .isInstanceOf(RetryableException.class)
                .hasMessage("HTTP 503 from job matcher")
                .hasCauseInstanceOf(RestClientResponseException.class);
        assertThatThrownBy(() -> client.compare(compareRequest()))
                .isInstanceOf(RetryableException.class)
                .hasMessage("HTTP 500 from job matcher")
                .hasCauseInstanceOf(RestClientResponseException.class);
    }

    @Test
    void connectionFailureIsRetryable() {
        wireMock.stop();

        assertThatThrownBy(() -> client.score(scoreRequest()))
                .isInstanceOf(RetryableException.class)
                .hasMessage("Could not reach job matcher")
                .hasCauseInstanceOf(ResourceAccessException.class);
        assertThatThrownBy(() -> client.compare(compareRequest()))
                .isInstanceOf(RetryableException.class)
                .hasMessage("Could not reach job matcher")
                .hasCauseInstanceOf(ResourceAccessException.class);
    }

    private String postedBody(String path) {
        List<LoggedRequest> requests = wireMock.findAll(postRequestedFor(urlEqualTo(path)));
        assertThat(requests).hasSize(1);
        LoggedRequest request = requests.get(0);
        assertThat(request.getHeader("Content-Type")).startsWith("application/json");
        return request.getBodyAsString();
    }

    private static ScoreRequest scoreRequest() {
        return new ScoreRequest(CANDIDATE_ID, "Backend engineer", blindCandidate());
    }

    private static CompareRequest compareRequest() {
        CompareCandidate first = new CompareCandidate(
                CANDIDATE_ID,
                blindCandidate().skills(),
                blindCandidate().yearsOfExperience(),
                blindCandidate().workHistory(),
                blindCandidate().education(),
                80.0,
                70.0,
                60.0,
                "solid backend fit");
        CompareCandidate second = new CompareCandidate(
                OTHER_ID,
                List.of("Go"),
                4.0,
                List.of(new WorkExperience("Northwind", "Developer", "2021 - 2024", "Shipped services")),
                List.of(new Education("City College", "B.A.", "Mathematics", "2014 - 2018")),
                75.0,
                65.0,
                55.0,
                "stronger skills");
        return new CompareRequest("Backend engineer", List.of(first, second));
    }

    private static BlindCandidate blindCandidate() {
        return new BlindCandidate(
                List.of("Java"),
                6.5,
                List.of(new WorkExperience("Acme", "Engineer", "2020 - 2024", "Built APIs")),
                List.of(new Education("State University", "B.S.", "Computer Science", "2016 - 2020")));
    }

    private static String scoreBody(boolean formulaVersion, boolean model) {
        String formula = formulaVersion ? ",\n  \"formula_version\": \"v1\"" : "";
        String modelField = model ? ",\n  \"model\": \"mock\"" : "";
        return """
                {
                  "candidate_id": "%s",
                  "skills_score": 80.0,
                  "experience_score": 70.0,
                  "education_score": 60.0,
                  "overall_score": 74.5,
                  "matched_skills": ["Java"],
                  "missing_skills": ["Go"],
                  "strengths": ["backend services"],
                  "concerns": ["short tenure"],
                  "summary": "solid backend fit"%s%s
                }
                """.formatted(CANDIDATE_ID, formula, modelField);
    }

    private static HireflowProperties properties(String matcherBaseUrl) {
        return new HireflowProperties(
                "http://127.0.0.1:9",
                matcherBaseUrl,
                new HireflowProperties.Retry(3, Duration.ofMillis(1)));
    }
}
