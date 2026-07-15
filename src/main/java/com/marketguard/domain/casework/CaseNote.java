package com.marketguard.domain.casework;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "case_note", indexes = @Index(name = "idx_case_note_case_time", columnList = "caseId,createdAt"))
public class CaseNote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long caseId;
    @Column(nullable = false, length = 120)
    private String author;
    @Column(nullable = false, length = 2_000)
    private String note;
    @Column(nullable = false)
    private Instant createdAt;

    public CaseNote(Long caseId, String author, String note, Instant createdAt) {
        this.caseId = caseId;
        this.author = author;
        this.note = note;
        this.createdAt = createdAt;
    }
}
