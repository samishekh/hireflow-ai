package com.hireflow.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ScoreMathTest {

    @Test
    void v1MatchesTheMatcherRounding() {
        assertThat(overall("50", "35", "15", 80, 60, 40)).isEqualTo(67.0);
        assertThat(overall("50", "35", "15", 100, 100, 100)).isEqualTo(100.0);
        assertThat(overall("50", "35", "15", 0, 0, 0)).isEqualTo(0.0);
        assertThat(overall("50", "35", "15", 90, 70, 40)).isEqualTo(75.5);
        assertThat(overall("50.00", "35.00", "15.00", 80, 70, 60)).isEqualTo(73.5);
        assertThat(overall("50", "35", "15", 40, 50, 45)).isEqualTo(44.3);
        assertThat(overall("50", "35", "15", 92, 88, 80)).isEqualTo(88.8);
    }

    @Test
    void customWeightsReweightWithoutTouchingTheScale() {
        assertThat(overall("70", "20", "10", 80, 70, 60)).isEqualTo(76.0);
        assertThat(overall("10", "10", "80", 100, 0, 0)).isEqualTo(10.0);
        assertThat(overall("10", "10", "80", 0, 0, 100)).isEqualTo(80.0);
        assertThat(overall("33.33", "33.33", "33.34", 80, 70, 60)).isEqualTo(70.0);
    }

    @Test
    void versionLabelKeepsV1ForTheDefaultWeights() {
        assertThat(ScoreMath.version(new BigDecimal("50.00"), new BigDecimal("35.0"), new BigDecimal("15")))
                .isEqualTo("v1");
        assertThat(ScoreMath.version(new BigDecimal("70.00"), new BigDecimal("20.00"), new BigDecimal("10.00")))
                .isEqualTo("70/20/10");
        assertThat(ScoreMath.version(new BigDecimal("33.30"), new BigDecimal("33.30"), new BigDecimal("33.40")))
                .isEqualTo("33.3/33.3/33.4");
    }

    @Test
    void weightRejectsOutOfRangeAndExtraDecimals() {
        assertThat(ScoreMath.weight(new BigDecimal("50"))).isEqualByComparingTo("50.00");
        assertThat(ScoreMath.weight(new BigDecimal("0"))).isEqualByComparingTo("0.00");
        assertThat(ScoreMath.weight(new BigDecimal("100.00"))).isEqualByComparingTo("100.00");
        assertThatThrownBy(() -> ScoreMath.weight(new BigDecimal("100.001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Each weight uses at most two decimal places");
        assertThatThrownBy(() -> ScoreMath.weight(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Each weight must be between 0 and 100");
        assertThatThrownBy(() -> ScoreMath.weight(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Each weight is required");
        assertThatThrownBy(() -> ScoreMath.requireSum(
                new BigDecimal("60.00"),
                new BigDecimal("30.00"),
                new BigDecimal("20.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Skills, experience, and education weights must add up to 100");
    }

    private static double overall(String skills, String experience, String education, double s, double e, double d) {
        return ScoreMath.overall(new BigDecimal(skills), new BigDecimal(experience), new BigDecimal(education), s, e, d);
    }
}
