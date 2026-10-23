package com.hireflow.core.domain;

import java.util.List;

public record BlindCandidate(
        List<String> skills,
        Double yearsOfExperience,
        List<WorkExperience> workHistory,
        List<Education> education) {

    public static BlindCandidate from(ParsedResume parsed) {
        return new BlindCandidate(
                parsed.skills(),
                parsed.yearsOfExperience(),
                parsed.workHistory(),
                parsed.education());
    }
}
