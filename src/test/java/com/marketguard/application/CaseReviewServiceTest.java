package com.marketguard.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.marketguard.audit.AuditService;
import com.marketguard.detection.casework.CaseStatus;
import com.marketguard.domain.casework.CaseNoteRepository;
import com.marketguard.domain.casework.CaseStatusHistoryRepository;
import com.marketguard.domain.casework.SurveillanceCase;
import com.marketguard.domain.casework.SurveillanceCaseRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CaseReviewServiceTest {

    @Mock SurveillanceCaseRepository caseRepository;
    @Mock CaseStatusHistoryRepository historyRepository;
    @Mock CaseNoteRepository noteRepository;
    @Mock CaseObservability observability;
    @Mock AuditService auditService;
    @Mock SurveillanceCase value;
    CaseReviewService service;

    @BeforeEach
    void setUp() {
        service = new CaseReviewService(caseRepository, historyRepository, noteRepository,
                observability, auditService, Clock.fixed(
                        Instant.parse("2026-07-15T01:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void rejectsAStaleVersionWithoutMutatingTheCase() {
        when(caseRepository.findById(1L)).thenReturn(Optional.of(value));
        when(value.getVersion()).thenReturn(3L);

        assertThatThrownBy(() -> service.updateStatus(
                1L, 2L, CaseStatus.REVIEWING, null, null, "operator"))
                .isInstanceOf(StaleCaseVersionException.class);

        verifyNoInteractions(historyRepository, noteRepository, observability, auditService);
    }

    @Test
    void appendsNotesAndAuditsOnlyMetadata() {
        when(caseRepository.findById(1L)).thenReturn(Optional.of(value));

        service.addNote(1L, "  공개 공시 확인 필요  ", "operator");

        verify(noteRepository).save(any());
        verify(observability).noteCreated();
        verify(auditService).record("CASE_NOTE_CREATED", "caseId=1 actor=operator length=11", "SUCCESS", 0L);
    }
}
