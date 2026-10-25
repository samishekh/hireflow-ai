package com.hireflow.core.integration;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Keeps {@code /parse-resume} from returning until the test releases it, so an override can be
 * attempted while the candidate is still in flight.
 */
final class ParseGate {

    private static volatile CountDownLatch open = new CountDownLatch(0);
    private static volatile String body = "";

    private ParseGate() {
    }

    static void hold(String responseBody) {
        body = responseBody;
        open = new CountDownLatch(1);
    }

    static void release() {
        open.countDown();
    }

    static String body() {
        return body;
    }

    static void awaitOpen() {
        CountDownLatch current = open;
        try {
            if (!current.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting to finish the parse call");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted waiting to finish the parse call", exception);
        }
    }
}
