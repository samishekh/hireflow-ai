package com.hireflow.core.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Request.Part;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.hireflow.core.config.HireflowProperties;
import com.hireflow.core.domain.Education;
import com.hireflow.core.domain.ParsedResume;
import com.hireflow.core.domain.WorkExperience;
import com.hireflow.core.service.PermanentException;
import com.hireflow.core.service.RetryableException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

class ParserClientTest {

    private static final String FILENAME = "maya-chen.pdf";
    private static final byte[] PDF = "%PDF-1.4\n% tiny resume\n".getBytes(StandardCharsets.US_ASCII);
    private static final String PARSE_JSON = """
            {
              "raw_text": "extracted text",
              "parsed_data": {
                "candidate_name": "Maya Chen",
                "email": "maya@example.com",
                "phone": "555-0100",
                "skills": ["Java"],
                "years_of_experience": 6.5,
                "work_history": [
                  {
                    "company": "Acme",
                    "role": "Engineer",
                    "duration": "2020 - 2024",
                    "description": "Built APIs"
                  }
                ],
                "education": [
                  {
                    "institution": "State University",
                    "degree": "B.S.",
                    "field_of_study": "Computer Science",
                    "duration": "2016 - 2020"
                  }
                ]
              }
            }
            """;

    private WireMockServer wireMock;
    private ParserClient client;

    @BeforeEach
    void setUp() {
        wireMock = TestRestClients.wireMock();
        client = new ParserClient(TestRestClients.snakeCase(), properties(wireMock.baseUrl()));
    }

    @AfterEach
    void tearDown() {
        if (wireMock.isRunning()) {
            wireMock.stop();
        }
    }

    @Test
    void parsePostsMultipartFileAndBindsSnakeCaseBody() {
        wireMock.stubFor(post(urlEqualTo("/parse-resume"))
                .withMultipartRequestBody(aMultipart()
                        .withName("file")
                        .withFileName(FILENAME)
                        .withHeader("Content-Type", containing("application/pdf"))
                        .withBody(binaryEqualTo(PDF)))
                .willReturn(okJson(PARSE_JSON)));

        ParseResumeResponse response = client.parse(FILENAME, PDF);

        List<LoggedRequest> requests = wireMock.findAll(postRequestedFor(urlEqualTo("/parse-resume")));
        assertThat(requests).hasSize(1);
        LoggedRequest request = requests.get(0);
        assertThat(request.getHeader("Content-Type")).startsWith("multipart/form-data");
        assertThat(request.isMultipart()).isTrue();
        assertThat(request.getParts()).hasSize(1);
        Part file = request.getPart("file");
        assertThat(file.getFileName()).isEqualTo(FILENAME);
        assertThat(file.getHeader("Content-Type").firstValue()).startsWith("application/pdf");
        assertThat(file.getBody().asBytes()).isEqualTo(PDF);
        ParsedResume parsed = response.parsedData();
        assertThat(response.rawText()).isEqualTo("extracted text");
        assertThat(parsed.candidateName()).isEqualTo("Maya Chen");
        assertThat(parsed.email()).isEqualTo("maya@example.com");
        assertThat(parsed.phone()).isEqualTo("555-0100");
        assertThat(parsed.skills()).containsExactly("Java");
        assertThat(parsed.yearsOfExperience()).isEqualTo(6.5);
        assertThat(parsed.workHistory()).containsExactly(
                new WorkExperience("Acme", "Engineer", "2020 - 2024", "Built APIs"));
        assertThat(parsed.education()).containsExactly(
                new Education("State University", "B.S.", "Computer Science", "2016 - 2020"));
    }

    @Test
    void emptyBodyIsPermanent() {
        wireMock.stubFor(post(urlEqualTo("/parse-resume")).willReturn(okJson("{}")));

        assertThatThrownBy(() -> client.parse(FILENAME, PDF))
                .isInstanceOf(PermanentException.class)
                .hasMessage("Resume parser returned an empty body")
                .hasNoCause();

        wireMock.resetAll();
        wireMock.stubFor(post(urlEqualTo("/parse-resume"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("")));

        assertThatThrownBy(() -> client.parse(FILENAME, PDF))
                .isInstanceOf(PermanentException.class)
                .hasMessage("Resume parser returned an empty body");
    }

    @Test
    void clientErrorIsPermanent() {
        wireMock.stubFor(post(urlEqualTo("/parse-resume")).willReturn(aResponse().withStatus(400)));

        assertThatThrownBy(() -> client.parse(FILENAME, PDF))
                .isInstanceOf(PermanentException.class)
                .hasMessage("HTTP 400 from resume parser")
                .hasCauseInstanceOf(RestClientResponseException.class);
    }

    @Test
    void serverErrorIsRetryable() {
        wireMock.stubFor(post(urlEqualTo("/parse-resume")).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client.parse(FILENAME, PDF))
                .isInstanceOf(RetryableException.class)
                .hasMessage("HTTP 503 from resume parser")
                .hasCauseInstanceOf(RestClientResponseException.class);
    }

    @Test
    void connectionFailureIsRetryable() {
        wireMock.stop();

        assertThatThrownBy(() -> client.parse(FILENAME, PDF))
                .isInstanceOf(RetryableException.class)
                .hasMessage("Could not reach resume parser")
                .hasCauseInstanceOf(ResourceAccessException.class);
    }

    private static HireflowProperties properties(String parserBaseUrl) {
        return new HireflowProperties(
                parserBaseUrl,
                "http://127.0.0.1:9",
                new HireflowProperties.Retry(3, Duration.ofMillis(1)));
    }
}
