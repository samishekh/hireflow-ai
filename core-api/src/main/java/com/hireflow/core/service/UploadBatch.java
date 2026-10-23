package com.hireflow.core.service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class UploadBatch {

    private final UUID jobId;
    private final Set<UUID> memberIds;
    private final Set<UUID> remaining;

    public UploadBatch(UUID jobId, Set<UUID> memberIds) {
        this.jobId = jobId;
        this.memberIds = Set.copyOf(memberIds);
        this.remaining = new HashSet<>(memberIds);
    }

    public UUID jobId() {
        return jobId;
    }

    public Set<UUID> memberIds() {
        return memberIds;
    }

    public Set<UUID> remaining() {
        return remaining;
    }
}
