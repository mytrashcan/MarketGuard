package com.marketguard.domain.casework;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseStatusHistoryRepository extends JpaRepository<CaseStatusHistory, Long> {
    List<CaseStatusHistory> findByCaseIdOrderByChangedAtAsc(Long caseId);
}
