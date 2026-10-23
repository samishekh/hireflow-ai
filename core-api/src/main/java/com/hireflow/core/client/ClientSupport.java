package com.hireflow.core.client;

import com.hireflow.core.service.PermanentException;
import com.hireflow.core.service.RetryableException;
import org.springframework.web.client.RestClientResponseException;

final class ClientSupport {

    private ClientSupport() {
    }

    static String trimSlash(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    static RuntimeException map(String service, RestClientResponseException exception) {
        String message = "HTTP " + exception.getStatusCode().value() + " from " + service;
        if (exception.getStatusCode().is5xxServerError()) {
            return new RetryableException(message, exception);
        }
        return new PermanentException(message, exception);
    }
}
