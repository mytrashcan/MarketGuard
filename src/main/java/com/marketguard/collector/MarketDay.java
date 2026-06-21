package com.marketguard.collector;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 특정 날짜의 KRX 개장 정보.
 * tradingDay=false면 휴장일. open/close는 정규장 시작·종료(KST), 모르면 null.
 */
public record MarketDay(LocalDate date, boolean tradingDay, LocalTime open, LocalTime close) {
}
