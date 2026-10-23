package com.hireflow.core.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CandidateRepository extends JpaRepository<CandidateEntity, UUID> {

    List<CandidateEntity> findByJobIdOrderByCreatedAtAsc(UUID jobId);

    @Query("""
            select c from CandidateEntity c
            where c.contentHash = :contentHash
              and c.parsedData is not null
              and c.id <> :candidateId
            order by c.createdAt asc
            """)
    List<CandidateEntity> findParsedCopies(
            @Param("contentHash") String contentHash,
            @Param("candidateId") UUID candidateId);
}
