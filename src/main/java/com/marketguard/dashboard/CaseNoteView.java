package com.marketguard.dashboard;

import com.marketguard.domain.casework.CaseNote;
import java.time.Instant;

public record CaseNoteView(Long id, String author, String note, Instant createdAt) {
    static CaseNoteView from(CaseNote value) {
        return new CaseNoteView(value.getId(), value.getAuthor(), value.getNote(), value.getCreatedAt());
    }
}
