package com.marketguard.domain.casework;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseNoteRepository extends JpaRepository<CaseNote, Long> {
    List<CaseNote> findByCaseIdOrderByCreatedAtAsc(Long caseId);
}
