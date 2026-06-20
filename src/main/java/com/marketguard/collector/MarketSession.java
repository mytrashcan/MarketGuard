package com.marketguard.collector;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Component;

/**
 * KRX(국내 증시) 정규장 운영 여부 판단.
 * 평일 09:00~15:30(KST)만 개장으로 본다.
 * ⚠️ 공휴일은 미반영(추후 토스 거래 캘린더 API 연동으로 보완 가능).
 */
@Component
public class MarketSession {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime OPEN = LocalTime.of(9, 0);
    private static final LocalTime CLOSE = LocalTime.of(15, 30);

    public boolean isKrxOpen() {
        ZonedDateTime now = ZonedDateTime.now(KST);
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        return !time.isBefore(OPEN) && !time.isAfter(CLOSE);
    }
}
