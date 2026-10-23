package com.hireflow.core.client;

import java.util.List;
import java.util.UUID;

public record ScoreResponse(
        UUID candidateId,
        double skillsScore,
        double experienceScore,
        double educationScore,
        double overallScore,
        List<String> matchedSkills,
        List<String> missingSkills,
        List<String> strengths,
        List<String> concerns,
        String summary,
        String formulaVersion,
        String model) {
}
