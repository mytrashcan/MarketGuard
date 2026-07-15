package com.marketguard.detection.model;

import java.time.LocalDate;

/**
 * 거래소 지정 경고/주의 정보(투자경고·투자위험·단기과열·정리매매·VI 등).
 * endDate가 null이면 진행 중.
 */
public record Warning(String type, LocalDate startDate, LocalDate endDate) {
    public Warning {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must not be before startDate");
        }
    }
}
