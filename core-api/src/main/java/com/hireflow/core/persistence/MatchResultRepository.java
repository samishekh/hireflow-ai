package com.hireflow.core.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchResultRepository extends JpaRepository<MatchResultEntity, UUID> {

    Optional<MatchResultEntity> findFirstByCacheKeyAndCandidateIdNotOrderByCreatedAtAsc(
            String cacheKey,
            UUID candidateId);

    List<MatchResultEntity> findByCandidateIdIn(Collection<UUID> candidateIds);
}
