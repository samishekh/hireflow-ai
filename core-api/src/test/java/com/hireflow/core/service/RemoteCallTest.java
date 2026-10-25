package com.hireflow.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hireflow.core.config.HireflowProperties;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RemoteCallTest {

    private final RemoteCall remoteCall = new RemoteCall(new HireflowProperties(
            "http://localhost:8000",
            "http://localhost:8001",
            new HireflowProperties.Retry(3, Duration.ofMillis(1))));

    @Test
    void retriesRetryableFailuresUntilSuccess() {
        AtomicInteger attempts = new AtomicInteger();

        String value = remoteCall.execute(() -> {
            if (attempts.incrementAndGet() < 2) {
                throw new RetryableException("connection reset", new IOException("connection reset"));
            }
            return "scored";
        });

        assertThat(value).isEqualTo("scored");
        assertThat(attempts).hasValue(2);
    }

    @Test
    void throwsPermanentExceptionOnTheFirstAttempt() {
        AtomicInteger attempts = new AtomicInteger();
        PermanentException permanent = new PermanentException("HTTP 400 from resume parser");

        assertThatThrownBy(() -> remoteCall.execute(() -> {
            attempts.incrementAndGet();
            throw permanent;
        })).isSameAs(permanent);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void rethrowsTheLastRetryableExceptionWhenAttemptsAreExhausted() {
        List<RetryableException> failures = List.of(
                new RetryableException("first", new IOException("first")),
                new RetryableException("second", new IOException("second")),
                new RetryableException("third", new IOException("third")));
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> remoteCall.execute(() -> {
            throw failures.get(attempts.getAndIncrement());
        })).isSameAs(failures.get(2));
        assertThat(attempts).hasValue(3);
    }
}
