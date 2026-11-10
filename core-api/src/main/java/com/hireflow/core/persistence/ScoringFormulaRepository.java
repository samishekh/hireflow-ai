package com.hireflow.core.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ScoringFormulaRepository extends JpaRepository<ScoringFormulaEntity, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select formula from ScoringFormulaEntity formula where formula.id = 1")
    Optional<ScoringFormulaEntity> lockSingleton();
}
