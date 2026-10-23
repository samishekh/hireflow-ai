package com.hireflow.core.client;

import com.hireflow.core.domain.Education;
import com.hireflow.core.domain.WorkExperience;
import java.util.List;
import java.util.UUID;

public record CompareCandidate(
        UUID candidateId,
        List<String> skills,
        Double yearsOfExperience,
        List<WorkExperience> workHistory,
        List<Education> education,
        double skillsScore,
        double experienceScore,
        double educationScore,
        String summary) {
}
