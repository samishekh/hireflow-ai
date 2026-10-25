package com.hireflow.core.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.hireflow.core.service.PermanentException;
import com.hireflow.core.service.RetryableException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClientResponseException;

class ClientSupportTest {

    @Test
    void serverErrorsAreRetryable() {
        assertMapped(500, "resume parser", RetryableException.class);
        assertMapped(503, "job matcher", RetryableException.class);
    }

    @Test
    void clientErrorsIncludingTooManyRequestsArePermanent() {
        assertMapped(400, "resume parser", PermanentException.class);
        assertMapped(429, "job matcher", PermanentException.class);
    }

    private static void assertMapped(int status, String service, Class<? extends RuntimeException> type) {
        RestClientResponseException http = new RestClientResponseException(
                "upstream",
                status,
                "status",
                new HttpHeaders(),
                new byte[0],
                StandardCharsets.UTF_8);
        RuntimeException mapped = ClientSupport.map(service, http);

        assertThat(mapped).isExactlyInstanceOf(type);
        assertThat(mapped).hasMessage("HTTP " + status + " from " + service);
        assertThat(mapped.getCause()).isSameAs(http);
    }
}
