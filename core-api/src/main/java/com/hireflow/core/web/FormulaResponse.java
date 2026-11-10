package com.hireflow.core.web;

import java.math.BigDecimal;
import java.time.Instant;

public record FormulaResponse(
        BigDecimal skillsWeight,
        BigDecimal experienceWeight,
        BigDecimal educationWeight,
        String formulaVersion,
        Instant updatedAt,
        int rescoredCandidates) {
}
