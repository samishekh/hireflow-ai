package com.hireflow.core.service;

import com.hireflow.core.config.HireflowProperties;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RemoteCall {

    private static final Logger logger = LoggerFactory.getLogger(RemoteCall.class);

    private final int maxAttempts;
    private final long initialDelayMs;

    public RemoteCall(HireflowProperties properties) {
        this.maxAttempts = properties.retry().maxAttempts();
        this.initialDelayMs = properties.retry().initialDelay().toMillis();
    }

    public <T> T execute(Supplier<T> call) {
        long delay = initialDelayMs;
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return call.get();
            } catch (PermanentException exception) {
                throw exception;
            } catch (RetryableException exception) {
                last = exception;
                if (attempt == maxAttempts) {
                    break;
                }
                logger.warn("Remote call attempt {} failed: {}", attempt, exception.getMessage());
                sleep(delay);
                delay *= 2;
            }
        }
        throw last;
    }

    private static void sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PermanentException("Retry interrupted", exception);
        }
    }
}
