package com.hireflow.core.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchResultRepository extends JpaRepository<MatchResultEntity, UUID> {

    Optional<MatchResultEntity> findFirstByCacheKeyAndCandidateIdNotOrderByCreatedAtAsc(
            String cacheKey,
            UUID candidateId);

    List<MatchResultEntity> findByCandidateIdIn(Collection<UUID> candidateIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update MatchResultEntity m
            set m.comparisonRank = null, m.comparisonReason = null
            where m.candidateId in (select c.id from CandidateEntity c where c.jobId = :jobId)
            """)
    int clearComparisonForJob(@Param("jobId") UUID jobId);
}
