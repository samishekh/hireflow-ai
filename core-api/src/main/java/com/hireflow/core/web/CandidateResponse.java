package com.hireflow.core.web;

import com.hireflow.core.domain.CandidateStatus;
import com.hireflow.core.domain.ParsedResume;
import java.util.UUID;

public record CandidateResponse(
        UUID id,
        UUID jobId,
        String originalFilename,
        CandidateStatus status,
        String errorMessage,
        String rawText,
        ParsedResume parsedData,
        ScoreBreakdown score) {
}
