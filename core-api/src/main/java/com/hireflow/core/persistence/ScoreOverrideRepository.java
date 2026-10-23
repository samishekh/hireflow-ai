package com.hireflow.core.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoreOverrideRepository extends JpaRepository<ScoreOverrideEntity, UUID> {

    List<ScoreOverrideEntity> findByCandidateIdInOrderByCreatedAtDesc(Collection<UUID> candidateIds);

    List<ScoreOverrideEntity> findByCandidateIdOrderByCreatedAtDesc(UUID candidateId);
}
