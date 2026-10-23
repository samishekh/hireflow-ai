package com.hireflow.core.web;

import com.hireflow.core.domain.OverrideDecision;
import java.time.Instant;
import java.util.UUID;

public record OverrideResponse(
        UUID id,
        UUID candidateId,
        OverrideDecision decision,
        String note,
        String decidedBy,
        Instant createdAt) {
}
