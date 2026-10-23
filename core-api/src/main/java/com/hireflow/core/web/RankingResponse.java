package com.hireflow.core.web;

import com.hireflow.core.domain.CandidateStatus;
import java.util.UUID;

public record RankingResponse(
        UUID id,
        String originalFilename,
        CandidateStatus status,
        String errorMessage,
        ScoreBreakdown score,
        OverrideResponse override) {
}
