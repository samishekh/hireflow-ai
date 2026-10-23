package com.hireflow.core.client;

import com.hireflow.core.domain.BlindCandidate;
import java.util.UUID;

public record ScoreRequest(UUID candidateId, String jobDescription, BlindCandidate candidate) {
}
