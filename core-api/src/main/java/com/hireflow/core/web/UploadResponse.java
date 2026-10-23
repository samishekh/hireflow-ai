package com.hireflow.core.web;

import java.util.List;
import java.util.UUID;

public record UploadResponse(List<UUID> candidateIds) {
}
