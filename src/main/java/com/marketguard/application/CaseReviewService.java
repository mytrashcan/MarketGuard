package com.marketguard.application;

import com.marketguard.audit.AuditService;
import com.marketguard.detection.casework.CaseStatus;
import com.marketguard.detection.casework.CaseTransitionPolicy;
import com.marketguard.detection.casework.ClosureReason;
import com.marketguard.domain.casework.CaseNote;
import com.marketguard.domain.casework.CaseNoteRepository;
import com.marketguard.domain.casework.CaseStatusHistory;
import com.marketguard.domain.casework.CaseStatusHistoryRepository;
import com.marketguard.domain.casework.SurveillanceCase;
import com.marketguard.domain.casework.SurveillanceCaseRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseReviewService {

    private final SurveillanceCaseRepository caseRepository;
    private final CaseStatusHistoryRepository historyRepository;
    private final CaseNoteRepository noteRepository;
    private final CaseTransitionPolicy transitionPolicy = new CaseTransitionPolicy();
    private final CaseObservability observability;
    private final AuditService auditService;
    private final Clock clock;

    public CaseReviewService(SurveillanceCaseRepository caseRepository,
                             CaseStatusHistoryRepository historyRepository,
                             CaseNoteRepository noteRepository,
                             CaseObservability observability,
                             AuditService auditService,
                             Clock clock) {
        this.caseRepository = caseRepository;
        this.historyRepository = historyRepository;
        this.noteRepository = noteRepository;
        this.observability = observability;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public SurveillanceCase updateStatus(Long caseId, long expectedVersion, CaseStatus target,
                                         ClosureReason reason, String detail, String actor) {
        SurveillanceCase value = find(caseId);
        if (value.getVersion() != expectedVersion) {
            throw new StaleCaseVersionException(caseId);
        }
        CaseStatus previous = value.getStatus();
        Instant now = clock.instant();
        value.transition(transitionPolicy, target, reason, normalize(detail), safeActor(actor), now);
        caseRepository.saveAndFlush(value);
        historyRepository.save(new CaseStatusHistory(caseId, previous, target, safeActor(actor),
                reason, normalize(detail), now));
        observability.transitioned(target);
        auditService.record("CASE_STATUS_CHANGED",
                "caseId=%d from=%s to=%s actor=%s".formatted(caseId, previous, target, safeActor(actor)),
                "SUCCESS", 0L);
        return value;
    }

    @Transactional
    public CaseNote addNote(Long caseId, String note, String actor) {
        find(caseId);
        String normalized = note == null ? null : note.trim();
        if (normalized == null || normalized.isBlank() || normalized.length() > 2_000) {
            throw new IllegalArgumentException("note must contain 1 to 2000 characters");
        }
        CaseNote saved = noteRepository.save(new CaseNote(caseId, safeActor(actor), normalized, clock.instant()));
        observability.noteCreated();
        auditService.record("CASE_NOTE_CREATED",
                "caseId=%d actor=%s length=%d".formatted(caseId, safeActor(actor), normalized.length()),
                "SUCCESS", 0L);
        return saved;
    }

    private SurveillanceCase find(Long id) {
        return caseRepository.findById(id).orElseThrow(() -> new CaseNotFoundException(id));
    }

    private static String safeActor(String actor) {
        if (actor == null || actor.isBlank()) {
            return "local-operator";
        }
        return actor.length() > 120 ? actor.substring(0, 120) : actor;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > 500) {
            throw new IllegalArgumentException("detail must not exceed 500 characters");
        }
        return trimmed;
    }
}
