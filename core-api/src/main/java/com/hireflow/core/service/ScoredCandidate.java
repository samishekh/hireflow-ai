package com.hireflow.core.service;

import com.hireflow.core.domain.BlindCandidate;
import java.time.Instant;
import java.util.UUID;

public record ScoredCandidate(
        UUID candidateId,
        Instant createdAt,
        double overallScore,
        BlindCandidate blind,
        double skillsScore,
        double experienceScore,
        double educationScore,
        String summary) {
}
