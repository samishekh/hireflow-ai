package com.hireflow.core.domain;

import java.util.List;

public record ParsedResume(
        String candidateName,
        String email,
        String phone,
        List<String> skills,
        Double yearsOfExperience,
        List<WorkExperience> workHistory,
        List<Education> education) {
}
