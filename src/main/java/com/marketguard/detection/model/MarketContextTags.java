package com.marketguard.detection.model;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** 현재 보유한 공식 시각만으로 확정할 수 있는 보수적인 시장 맥락 태그. */
public final class MarketContextTags {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime OPEN = LocalTime.of(9, 0);
    private static final LocalTime OPENING_WINDOW_END = LocalTime.of(9, 10);
    private static final LocalTime CLOSING_WINDOW_START = LocalTime.of(15, 20);
    private static final LocalTime CLOSE = LocalTime.of(15, 30);

    private MarketContextTags() {
    }

    public static List<String> at(Instant instant, String... additional) {
        LocalTime time = instant.atZone(KST).toLocalTime();
        List<String> tags = new ArrayList<>();
        if (!time.isBefore(OPEN) && !time.isAfter(CLOSE)) {
            tags.add("장중");
        }
        if (!time.isBefore(OPEN) && time.isBefore(OPENING_WINDOW_END)) {
            tags.add("장 시작 직후");
        }
        if (!time.isBefore(CLOSING_WINDOW_START) && !time.isAfter(CLOSE)) {
            tags.add("장 마감 직전");
        }
        if (additional != null) {
            for (String value : additional) {
                if (value != null && !value.isBlank()) {
                    tags.add(value);
                }
            }
        }
        return tags.stream().distinct().toList();
    }
}
