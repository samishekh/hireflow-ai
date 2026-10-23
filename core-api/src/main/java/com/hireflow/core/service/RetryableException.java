package com.hireflow.core.service;

public class RetryableException extends RuntimeException {

    public RetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
