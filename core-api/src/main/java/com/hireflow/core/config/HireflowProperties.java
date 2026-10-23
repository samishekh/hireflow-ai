package com.hireflow.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hireflow")
public record HireflowProperties(String parserBaseUrl, String matcherBaseUrl, Retry retry) {

    public record Retry(int maxAttempts, Duration initialDelay) {
    }
}
