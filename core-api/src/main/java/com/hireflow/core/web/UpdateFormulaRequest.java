package com.hireflow.core.web;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UpdateFormulaRequest(
        @NotNull BigDecimal skillsWeight,
        @NotNull BigDecimal experienceWeight,
        @NotNull BigDecimal educationWeight) {
}
