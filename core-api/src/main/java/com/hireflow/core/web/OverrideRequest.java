package com.hireflow.core.web;

import com.hireflow.core.domain.OverrideDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OverrideRequest(
        @NotNull OverrideDecision decision,
        @NotBlank @Size(max = 4000) String note,
        @NotBlank @Size(max = 200) String decidedBy) {
}
