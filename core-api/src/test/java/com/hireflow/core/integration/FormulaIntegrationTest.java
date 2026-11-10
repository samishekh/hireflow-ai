package com.hireflow.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.hireflow.core.domain.OverrideDecision;
import com.hireflow.core.persistence.MatchResultRepository;
import com.hireflow.core.persistence.ScoringFormulaEntity;
import com.hireflow.core.service.ScoreMath;
import com.hireflow.core.web.CandidateResponse;
import com.hireflow.core.web.FormulaResponse;
import com.hireflow.core.web.RankingResponse;
import com.hireflow.core.web.ScoreBreakdown;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class FormulaIntegrationTest extends PipelineTestSupport {

    @Autowired
    private MatchResultRepository matches;

    @Test
    void getReturnsTheDefaultFormula() {
        FormulaResponse formula = getFormula();

        assertThat(formula.skillsWeight()).isEqualByComparingTo("50");
        assertThat(formula.experienceWeight()).isEqualByComparingTo("35");
        assertThat(formula.educationWeight()).isEqualByComparingTo("15");
        assertThat(formula.formulaVersion()).isEqualTo("v1");
        assertThat(formula.updatedAt()).isNotNull();
        assertThat(formula.rescoredCandidates()).isZero();
    }

    @Test
    void invalidWeightsLeaveStoredScoresOnV1() {
        String skill = "formula-reject-skill";
        stubParseAndScore(skill, 80, 70, 60, 99);
        UUID jobId = createJob("Reject weights");
        UUID candidateId = upload(jobId, pdf("reject.pdf", skill)).getFirst();
        CandidateResponse done = awaitTerminal(candidateId);
        assertThat(done.score().overallScore()).isEqualTo(v1(80, 70, 60));
        int scores = requestCount("/score");

        ResponseEntity<String> sum = putFormula(weights(60, 30, 20));
        assertThat(sum.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(sum.getBody()).contains("must add up to 100");

        ResponseEntity<String> decimals = putFormula("""
                {"skills_weight":"60.001","experience_weight":"30","education_weight":"9.999"}
                """);
        assertThat(decimals.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(decimals.getBody()).contains("at most two decimal places");

        ResponseEntity<String> negative = putFormula(weights(-5, 90, 15));
        assertThat(negative.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(negative.getBody()).contains("between 0 and 100");

        ResponseEntity<String> missing = putFormula("""
                {"skills_weight":50,"experience_weight":35}
                """);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        FormulaResponse formula = getFormula();
        assertThat(formula.formulaVersion()).isEqualTo("v1");
        ScoreBreakdown score = getCandidate(candidateId).score();
        assertThat(score.skillsScore()).isEqualTo(80);
        assertThat(score.experienceScore()).isEqualTo(70);
        assertThat(score.educationScore()).isEqualTo(60);
        assertThat(score.overallScore()).isEqualTo(v1(80, 70, 60));
        assertThat(score.formulaVersion()).isEqualTo("v1");
        assertThat(requestCount("/score")).isEqualTo(scores);
    }

    @Test
    void changeRescoresEveryJobAndNewUploadsUseOnlyTheNewFormula() {
        String skillsPerson = "formula-skills-person";
        String educationPerson = "formula-education-person";
        String otherJob = "formula-other-job";
        stubParseAndScore(skillsPerson, 100, 0, 0, 1);
        stubParseAndScore(educationPerson, 0, 0, 100, 2);
        stubParseAndScore(otherJob, 80, 70, 60, 99);

        UUID firstJob = createJob("Skills versus education");
        List<UUID> firstIds = upload(
                firstJob,
                pdf("skills.pdf", skillsPerson),
                pdf("education.pdf", educationPerson));
        UUID skillsId = firstIds.get(0);
        UUID educationId = firstIds.get(1);
        assertDone(awaitTerminal(skillsId));
        assertDone(awaitTerminal(educationId));

        UUID secondJob = createJob("Other role");
        UUID otherId = upload(secondJob, pdf("other.pdf", otherJob)).getFirst();
        assertDone(awaitTerminal(otherId));
        ResponseEntity<String> override = postOverride(otherId);
        assertThat(override.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        List<RankingResponse> before = rankings(firstJob);
        assertThat(before).extracting(RankingResponse::originalFilename).containsExactly("skills.pdf", "education.pdf");
        int scoreCalls = requestCount("/score");
        long stored = matches.count();

        ResponseEntity<String> same = putFormula(weights(50, 35, 15));
        assertThat(same.getStatusCode()).isEqualTo(HttpStatus.OK);
        FormulaResponse unchanged = read(same.getBody(), FormulaResponse.class);
        assertThat(unchanged.rescoredCandidates()).isZero();
        assertThat(unchanged.formulaVersion()).isEqualTo("v1");

        ResponseEntity<String> updated = putFormula("""
                {"skills_weight":"10","experience_weight":"10.00","education_weight":"80"}
                """);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        FormulaResponse formula = read(updated.getBody(), FormulaResponse.class);
        assertThat(formula.skillsWeight()).isEqualByComparingTo("10");
        assertThat(formula.experienceWeight()).isEqualByComparingTo("10");
        assertThat(formula.educationWeight()).isEqualByComparingTo("80");
        assertThat(formula.formulaVersion()).isEqualTo("10/10/80");
        assertThat(formula.rescoredCandidates()).isEqualTo(Math.toIntExact(stored));
        assertThat(requestCount("/score")).isEqualTo(scoreCalls);

        List<RankingResponse> after = rankings(firstJob);
        assertThat(after).extracting(RankingResponse::originalFilename).containsExactly("education.pdf", "skills.pdf");
        assertThat(after.get(0).score().overallScore()).isEqualTo(weighted(10, 10, 80, 0, 0, 100));
        assertThat(after.get(0).score().educationScore()).isEqualTo(100);
        assertThat(after.get(0).score().formulaVersion()).isEqualTo("10/10/80");
        assertThat(after.get(1).score().overallScore()).isEqualTo(weighted(10, 10, 80, 100, 0, 0));
        assertThat(after.get(1).score().skillsScore()).isEqualTo(100);

        CandidateResponse rescored = getCandidate(otherId);
        assertThat(rescored.score().skillsScore()).isEqualTo(80);
        assertThat(rescored.score().experienceScore()).isEqualTo(70);
        assertThat(rescored.score().educationScore()).isEqualTo(60);
        assertThat(rescored.score().overallScore()).isEqualTo(weighted(10, 10, 80, 80, 70, 60));
        assertThat(rescored.score().overallScore()).isNotEqualTo(99);
        assertThat(rescored.score().formulaVersion()).isEqualTo("10/10/80");
        assertThat(rescored.score().model()).isEqualTo("mock");
        assertThat(overrides.findByCandidateIdOrderByCreatedAtDesc(otherId)).hasSize(1);
        assertThat(overrides.findByCandidateIdOrderByCreatedAtDesc(otherId).getFirst().getDecision())
                .isEqualTo(OverrideDecision.accepted);

        String freshSkill = "formula-fresh-upload";
        stubParseAndScore(freshSkill, 80, 70, 60, 99);
        UUID freshId = upload(secondJob, pdf("fresh.pdf", freshSkill)).getFirst();
        CandidateResponse fresh = awaitTerminal(freshId);
        assertDone(fresh);
        assertThat(fresh.score().overallScore()).isEqualTo(weighted(10, 10, 80, 80, 70, 60));
        assertThat(fresh.score().formulaVersion()).isEqualTo("10/10/80");
        assertThat(fresh.score().model()).isEqualTo("mock");
        assertThat(requestCount("/score")).isEqualTo(scoreCalls + 1);

        byte[] sameBytes = pdf("fresh.pdf", freshSkill).bytes();
        UUID copiedId = upload(secondJob, new NamedPdf("fresh-copy.pdf", sameBytes)).getFirst();
        CandidateResponse copied = awaitTerminal(copiedId);
        assertDone(copied);
        assertThat(requestCount("/score")).isEqualTo(scoreCalls + 1);
        assertThat(copied.score().skillsScore()).isEqualTo(fresh.score().skillsScore());
        assertThat(copied.score().experienceScore()).isEqualTo(fresh.score().experienceScore());
        assertThat(copied.score().educationScore()).isEqualTo(fresh.score().educationScore());
        assertThat(copied.score().overallScore()).isEqualTo(fresh.score().overallScore());
        assertThat(copied.score().formulaVersion()).isEqualTo("10/10/80");

        FormulaResponse reread = getFormula();
        ResponseEntity<String> repeat = putFormula(weights(10, 10, 80));
        FormulaResponse repeatBody = read(repeat.getBody(), FormulaResponse.class);
        assertThat(repeatBody.rescoredCandidates()).isZero();
        assertThat(repeatBody.updatedAt()).isEqualTo(reread.updatedAt());
        assertThat(getCandidate(freshId).score().overallScore()).isEqualTo(fresh.score().overallScore());
    }

    @Test
    void cachedUploadUsesTheCurrentFormulaWhenTheSourceRowIsStillOnTheOldOne() {
        String skill = "formula-stale-cache";
        stubParseAndScore(skill, 80, 70, 60, 99);
        UUID jobId = createJob("Stale cache");
        byte[] bytes = pdf("stale.pdf", skill).bytes();
        UUID firstId = upload(jobId, new NamedPdf("stale.pdf", bytes)).getFirst();
        CandidateResponse first = awaitTerminal(firstId);
        assertThat(first.score().overallScore()).isEqualTo(v1(80, 70, 60));
        assertThat(first.score().formulaVersion()).isEqualTo("v1");

        ScoringFormulaEntity formula = formulas.findById(ScoringFormulaEntity.ID).orElseThrow();
        formula.setSkillsWeight(new BigDecimal("10.00"));
        formula.setExperienceWeight(new BigDecimal("10.00"));
        formula.setEducationWeight(new BigDecimal("80.00"));
        formula.setFormulaVersion("10/10/80");
        formulas.saveAndFlush(formula);

        UUID secondId = upload(jobId, new NamedPdf("stale-copy.pdf", bytes)).getFirst();
        CandidateResponse second = awaitTerminal(secondId);
        assertThat(requestCount("/score")).isEqualTo(1);
        assertThat(second.score().overallScore()).isEqualTo(weighted(10, 10, 80, 80, 70, 60));
        assertThat(second.score().formulaVersion()).isEqualTo("10/10/80");
        assertThat(second.score().skillsScore()).isEqualTo(80);
        assertThat(getCandidate(firstId).score().overallScore()).isEqualTo(first.score().overallScore());
        assertThat(getCandidate(firstId).score().formulaVersion()).isEqualTo("v1");
    }

    private static String weights(int skills, int experience, int education) {
        return """
                {"skills_weight":%d,"experience_weight":%d,"education_weight":%d}
                """.formatted(skills, experience, education);
    }

    private static double v1(double skills, double experience, double education) {
        return weighted(50, 35, 15, skills, experience, education);
    }

    private static double weighted(
            int skillsWeight,
            int experienceWeight,
            int educationWeight,
            double skills,
            double experience,
            double education) {
        return ScoreMath.overall(
                BigDecimal.valueOf(skillsWeight),
                BigDecimal.valueOf(experienceWeight),
                BigDecimal.valueOf(educationWeight),
                skills,
                experience,
                education);
    }
}
