package com.hireflow.core.client;

import java.util.List;

public record CompareRequest(String jobDescription, List<CompareCandidate> candidates) {
}
