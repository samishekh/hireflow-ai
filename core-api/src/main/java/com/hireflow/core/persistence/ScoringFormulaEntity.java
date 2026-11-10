package com.hireflow.core.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "scoring_formula")
public class ScoringFormulaEntity {

    public static final short ID = 1;

    @Id
    private short id;

    @Column(name = "skills_weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal skillsWeight;

    @Column(name = "experience_weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal experienceWeight;

    @Column(name = "education_weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal educationWeight;

    @Column(name = "formula_version", nullable = false, length = 32)
    private String formulaVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public short getId() {
        return id;
    }

    public void setId(short id) {
        this.id = id;
    }

    public BigDecimal getSkillsWeight() {
        return skillsWeight;
    }

    public void setSkillsWeight(BigDecimal skillsWeight) {
        this.skillsWeight = skillsWeight;
    }

    public BigDecimal getExperienceWeight() {
        return experienceWeight;
    }

    public void setExperienceWeight(BigDecimal experienceWeight) {
        this.experienceWeight = experienceWeight;
    }

    public BigDecimal getEducationWeight() {
        return educationWeight;
    }

    public void setEducationWeight(BigDecimal educationWeight) {
        this.educationWeight = educationWeight;
    }

    public String getFormulaVersion() {
        return formulaVersion;
    }

    public void setFormulaVersion(String formulaVersion) {
        this.formulaVersion = formulaVersion;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
