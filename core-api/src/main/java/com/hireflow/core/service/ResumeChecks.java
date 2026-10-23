package com.hireflow.core.service;

import com.hireflow.core.domain.BlindCandidate;
import com.hireflow.core.domain.Education;
import com.hireflow.core.domain.ParsedResume;
import com.hireflow.core.domain.WorkExperience;
import java.util.ArrayList;
import java.util.List;

public final class ResumeChecks {

    private ResumeChecks() {
    }

    public static BlindCandidate blind(ParsedResume parsed) {
        if (parsed == null
                || parsed.skills() == null
                || parsed.yearsOfExperience() == null
                || parsed.workHistory() == null
                || parsed.education() == null) {
            throw new PermanentException("Parsed resume is missing the blind scoring fields");
        }
        for (String skill : parsed.skills()) {
            if (skill == null) {
                throw new PermanentException("Parsed resume contains a null skill");
            }
        }
        for (WorkExperience work : parsed.workHistory()) {
            requireText(work.company(), work.role(), work.duration(), work.description());
        }
        for (Education education : parsed.education()) {
            requireText(education.institution(), education.degree(), education.fieldOfStudy(), education.duration());
        }
        return BlindCandidate.from(parsed);
    }

    private static void requireText(String... values) {
        for (String value : values) {
            if (value == null) {
                throw new PermanentException("Parsed resume is missing a required text field");
            }
        }
    }

    public static List<String> copyList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return new ArrayList<>(values);
    }
}
