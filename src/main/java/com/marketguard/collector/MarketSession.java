package com.marketguard.collector;

import com.marketguard.collector.client.TossMarketDataClient;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * KRX(국내 증시) 정규장 운영 여부 판단.
 * 토스 거래캘린더(공휴일·정규장 시간)를 일자별로 1회 조회·캐싱해서 사용하고,
 * 조회 실패 시 평일 09:00~15:30(KST) 기본값으로 폴백한다.
 */
@Slf4j
@Component
public class MarketSession {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime DEFAULT_OPEN = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_CLOSE = LocalTime.of(15, 30);

    private final TossMarketDataClient marketDataClient;
    private volatile MarketDay cachedToday;   // 오늘 캘린더 캐시

    public MarketSession(TossMarketDataClient marketDataClient) {
        this.marketDataClient = marketDataClient;
    }

    public boolean isKrxOpen() {
        ZonedDateTime now = ZonedDateTime.now(KST);
        LocalDate today = now.toLocalDate();
        LocalTime time = now.toLocalTime();

        MarketDay calendar = calendarFor(today);
        if (calendar != null) {
            if (!calendar.tradingDay()) {
                return false;   // 공휴일 등 휴장
            }
            LocalTime open = calendar.open() != null ? calendar.open() : DEFAULT_OPEN;
            LocalTime close = calendar.close() != null ? calendar.close() : DEFAULT_CLOSE;
            return !time.isBefore(open) && !time.isAfter(close);
        }

        // 폴백: 캘린더를 모를 때는 평일 + 기본 시간
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        return !time.isBefore(DEFAULT_OPEN) && !time.isAfter(DEFAULT_CLOSE);
    }

    private MarketDay calendarFor(LocalDate today) {
        MarketDay cached = cachedToday;
        if (cached != null && today.equals(cached.date())) {
            return cached;
        }
        try {
            MarketDay fetched = marketDataClient.fetchKrMarketToday().orElse(null);
            if (fetched != null && today.equals(fetched.date())) {
                cachedToday = fetched;
                return fetched;
            }
        } catch (Exception e) {
            log.debug("거래캘린더 조회 실패 — 기본 운영시간으로 폴백: {}", e.getMessage());
        }
        return null;
    }
}
