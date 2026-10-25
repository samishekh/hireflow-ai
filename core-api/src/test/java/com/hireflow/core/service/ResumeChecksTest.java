package com.hireflow.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hireflow.core.domain.BlindCandidate;
import com.hireflow.core.domain.Education;
import com.hireflow.core.domain.ParsedResume;
import com.hireflow.core.domain.WorkExperience;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeChecksTest {

    @Test
    void blindDropsNameEmailAndPhone() {
        WorkExperience work = new WorkExperience("Northwind", "Engineer", "2019-2024", "Built APIs");
        Education education = new Education("State University", "BS", "Computer Science", "2015-2019");
        ParsedResume parsed = new ParsedResume(
                "Maya Chen",
                "maya@example.com",
                "555-0199",
                List.of("Java", "PostgreSQL"),
                6.0,
                List.of(work),
                List.of(education));

        BlindCandidate blind = ResumeChecks.blind(parsed);

        assertThat(parsed.candidateName()).isEqualTo("Maya Chen");
        assertThat(parsed.email()).isEqualTo("maya@example.com");
        assertThat(parsed.phone()).isEqualTo("555-0199");
        assertThat(blind.skills()).containsExactly("Java", "PostgreSQL");
        assertThat(blind.yearsOfExperience()).isEqualTo(6.0);
        assertThat(blind.workHistory()).containsExactly(work);
        assertThat(blind.education()).containsExactly(education);
        assertThat(BlindCandidate.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("candidateName", "email", "phone");
    }

    @Test
    void nullSkillThrowsPermanentException() {
        List<String> skills = new ArrayList<>();
        skills.add("Java");
        skills.add(null);
        ParsedResume parsed = resume(skills, new WorkExperience("Northwind", "Engineer", "2019-2024", "Built APIs"));

        assertThatThrownBy(() -> ResumeChecks.blind(parsed))
                .isInstanceOf(PermanentException.class)
                .hasMessage("Parsed resume contains a null skill");
    }

    @Test
    void nullWorkFieldThrowsPermanentException() {
        ParsedResume parsed = resume(
                List.of("Java"),
                new WorkExperience("Northwind", null, "2019-2024", "Built APIs"));

        assertThatThrownBy(() -> ResumeChecks.blind(parsed))
                .isInstanceOf(PermanentException.class)
                .hasMessage("Parsed resume is missing a required text field");
    }

    private static ParsedResume resume(List<String> skills, WorkExperience work) {
        return new ParsedResume(
                "Maya Chen",
                "maya@example.com",
                "555-0199",
                skills,
                6.0,
                List.of(work),
                List.of(new Education("State University", "BS", "Computer Science", "2015-2019")));
    }
}
