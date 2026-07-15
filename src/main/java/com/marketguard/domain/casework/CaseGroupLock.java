package com.marketguard.domain.casework;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "case_group_lock")
public class CaseGroupLock {
    @Id
    @Column(length = 20)
    private String stockCode;
    @Column(nullable = false)
    private Instant updatedAt;
}
