package com.hireflow.core.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "match_results")
public class MatchResultEntity {

    @Id
    @Column(name = "candidate_id")
    private UUID candidateId;

    @Column(name = "skills_score", nullable = false)
    private double skillsScore;

    @Column(name = "experience_score", nullable = false)
    private double experienceScore;

    @Column(name = "education_score", nullable = false)
    private double educationScore;

    @Column(name = "overall_score", nullable = false)
    private double overallScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matched_skills", nullable = false, columnDefinition = "jsonb")
    private List<String> matchedSkills = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_skills", nullable = false, columnDefinition = "jsonb")
    private List<String> missingSkills = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> strengths = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> concerns = new ArrayList<>();

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Column(name = "formula_version", nullable = false, length = 32)
    private String formulaVersion;

    @Column(name = "comparison_rank")
    private Integer comparisonRank;

    @Column(name = "comparison_reason", columnDefinition = "text")
    private String comparisonReason;

    @Column(nullable = false, length = 128)
    private String model;

    @Column(name = "cache_key", nullable = false, length = 64)
    private String cacheKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(UUID candidateId) {
        this.candidateId = candidateId;
    }

    public double getSkillsScore() {
        return skillsScore;
    }

    public void setSkillsScore(double skillsScore) {
        this.skillsScore = skillsScore;
    }

    public double getExperienceScore() {
        return experienceScore;
    }

    public void setExperienceScore(double experienceScore) {
        this.experienceScore = experienceScore;
    }

    public double getEducationScore() {
        return educationScore;
    }

    public void setEducationScore(double educationScore) {
        this.educationScore = educationScore;
    }

    public double getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(double overallScore) {
        this.overallScore = overallScore;
    }

    public List<String> getMatchedSkills() {
        return matchedSkills;
    }

    public void setMatchedSkills(List<String> matchedSkills) {
        this.matchedSkills = matchedSkills;
    }

    public List<String> getMissingSkills() {
        return missingSkills;
    }

    public void setMissingSkills(List<String> missingSkills) {
        this.missingSkills = missingSkills;
    }

    public List<String> getStrengths() {
        return strengths;
    }

    public void setStrengths(List<String> strengths) {
        this.strengths = strengths;
    }

    public List<String> getConcerns() {
        return concerns;
    }

    public void setConcerns(List<String> concerns) {
        this.concerns = concerns;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getFormulaVersion() {
        return formulaVersion;
    }

    public void setFormulaVersion(String formulaVersion) {
        this.formulaVersion = formulaVersion;
    }

    public Integer getComparisonRank() {
        return comparisonRank;
    }

    public void setComparisonRank(Integer comparisonRank) {
        this.comparisonRank = comparisonRank;
    }

    public String getComparisonReason() {
        return comparisonReason;
    }

    public void setComparisonReason(String comparisonReason) {
        this.comparisonReason = comparisonReason;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getCacheKey() {
        return cacheKey;
    }

    public void setCacheKey(String cacheKey) {
        this.cacheKey = cacheKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
