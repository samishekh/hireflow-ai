package com.hireflow.core.client;

import java.util.UUID;

public record ComparisonEntry(UUID candidateId, int rank, String reason) {
}
