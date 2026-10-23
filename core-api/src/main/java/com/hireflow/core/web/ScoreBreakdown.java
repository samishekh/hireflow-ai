package com.hireflow.core.web;

import java.util.List;

public record ScoreBreakdown(
        Double skillsScore,
        Double experienceScore,
        Double educationScore,
        Double overallScore,
        List<String> matchedSkills,
        List<String> missingSkills,
        List<String> strengths,
        List<String> concerns,
        String summary,
        String formulaVersion,
        Integer comparisonRank,
        String comparisonReason,
        String model) {
}
