package com.hireflow.core.service;

import com.hireflow.core.persistence.MatchResultEntity;
import com.hireflow.core.persistence.MatchResultRepository;
import com.hireflow.core.persistence.ScoringFormulaEntity;
import com.hireflow.core.persistence.ScoringFormulaRepository;
import com.hireflow.core.web.FormulaResponse;
import com.hireflow.core.web.UpdateFormulaRequest;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ScoringFormulaService {

    private final ScoringFormulaRepository formulas;
    private final MatchResultRepository matches;

    public ScoringFormulaService(ScoringFormulaRepository formulas, MatchResultRepository matches) {
        this.formulas = formulas;
        this.matches = matches;
    }

    @Transactional(readOnly = true)
    public FormulaResponse get() {
        return response(require(), 0);
    }

    @Transactional
    public ScoringFormulaEntity lock() {
        return formulas.lockSingleton().orElseThrow(ScoringFormulaService::missing);
    }

    @Transactional
    public FormulaResponse update(UpdateFormulaRequest request) {
        ScoringFormulaEntity formula = lock();
        BigDecimal skills = readWeight(request.skillsWeight());
        BigDecimal experience = readWeight(request.experienceWeight());
        BigDecimal education = readWeight(request.educationWeight());
        try {
            ScoreMath.requireSum(skills, experience, education);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        if (formula.getSkillsWeight().compareTo(skills) == 0
                && formula.getExperienceWeight().compareTo(experience) == 0
                && formula.getEducationWeight().compareTo(education) == 0) {
            return response(formula, 0);
        }
        formula.setSkillsWeight(skills);
        formula.setExperienceWeight(experience);
        formula.setEducationWeight(education);
        formula.setFormulaVersion(ScoreMath.version(skills, experience, education));
        formula.setUpdatedAt(Instant.now());
        int rescored = 0;
        for (MatchResultEntity row : matches.findAll()) {
            apply(formula, row);
            rescored++;
        }
        return response(formula, rescored);
    }

    public void apply(ScoringFormulaEntity formula, MatchResultEntity row) {
        row.setOverallScore(ScoreMath.overall(
                formula.getSkillsWeight(),
                formula.getExperienceWeight(),
                formula.getEducationWeight(),
                row.getSkillsScore(),
                row.getExperienceScore(),
                row.getEducationScore()));
        row.setFormulaVersion(formula.getFormulaVersion());
    }

    private ScoringFormulaEntity require() {
        return formulas.findById(ScoringFormulaEntity.ID).orElseThrow(ScoringFormulaService::missing);
    }

    private static BigDecimal readWeight(BigDecimal value) {
        try {
            return ScoreMath.weight(value);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    private static FormulaResponse response(ScoringFormulaEntity formula, int rescoredCandidates) {
        return new FormulaResponse(
                formula.getSkillsWeight(),
                formula.getExperienceWeight(),
                formula.getEducationWeight(),
                formula.getFormulaVersion(),
                formula.getUpdatedAt(),
                rescoredCandidates);
    }

    private static IllegalStateException missing() {
        return new IllegalStateException("Scoring formula is missing");
    }
}
