package com.hireflow.core.client;

import java.util.List;

public record CompareResponse(List<ComparisonEntry> rankings, String model) {
}
