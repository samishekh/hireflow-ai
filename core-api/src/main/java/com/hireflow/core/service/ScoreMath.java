package com.hireflow.core.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ScoreMath {

    public static final BigDecimal V1_SKILLS = new BigDecimal("50.00");
    public static final BigDecimal V1_EXPERIENCE = new BigDecimal("35.00");
    public static final BigDecimal V1_EDUCATION = new BigDecimal("15.00");
    public static final String V1 = "v1";

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal HUNDRED_SCALED = new BigDecimal("100.00");

    private ScoreMath() {
    }

    public static BigDecimal weight(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("Each weight is required");
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Each weight uses at most two decimal places");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("Each weight must be between 0 and 100");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static void requireSum(BigDecimal skills, BigDecimal experience, BigDecimal education) {
        BigDecimal sum = skills.add(experience).add(education);
        if (sum.compareTo(HUNDRED_SCALED) != 0) {
            throw new IllegalArgumentException(
                    "Skills, experience, and education weights must add up to 100");
        }
    }

    public static String version(BigDecimal skills, BigDecimal experience, BigDecimal education) {
        if (skills.compareTo(V1_SKILLS) == 0
                && experience.compareTo(V1_EXPERIENCE) == 0
                && education.compareTo(V1_EDUCATION) == 0) {
            return V1;
        }
        return plain(skills) + "/" + plain(experience) + "/" + plain(education);
    }

    public static double overall(
            BigDecimal skillsWeight,
            BigDecimal experienceWeight,
            BigDecimal educationWeight,
            double skillsScore,
            double experienceScore,
            double educationScore) {
        BigDecimal total = skillsWeight.multiply(decimal(skillsScore))
                .add(experienceWeight.multiply(decimal(experienceScore)))
                .add(educationWeight.multiply(decimal(educationScore)))
                .divide(HUNDRED, 8, RoundingMode.HALF_UP);
        return total.setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    public static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal decimal(double score) {
        return new BigDecimal(Double.toString(score));
    }
}
