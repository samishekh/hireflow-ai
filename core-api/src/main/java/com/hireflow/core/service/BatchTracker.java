package com.hireflow.core.service;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class BatchTracker {

    private final CompareService compareService;

    public BatchTracker(CompareService compareService) {
        this.compareService = compareService;
    }

    public void finished(UploadBatch batch, UUID candidateId) {
        boolean last;
        synchronized (batch) {
            if (!batch.remaining().remove(candidateId)) {
                return;
            }
            last = batch.remaining().isEmpty();
        }
        if (last) {
            compareService.onBatchComplete(batch.jobId(), batch.memberIds());
        }
    }
}
