package com.hireflow.core.client;

import com.hireflow.core.domain.ParsedResume;

public record ParseResumeResponse(String rawText, ParsedResume parsedData) {
}
