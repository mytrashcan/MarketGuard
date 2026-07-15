package com.marketguard.detection.casework;

/** 운영자 종결 사유. PUBLIC_EVENT는 확인된 공개 정보만 기록한다. */
public enum ClosureReason {
    MARKET_WIDE_MOVE,
    SECTOR_WIDE_MOVE,
    OPEN_OR_CLOSE_EFFECT,
    EXPECTED_HIGH_VOLATILITY,
    PUBLIC_EVENT,
    DATA_ERROR,
    TEMPORARY_ORDERBOOK_DISTORTION,
    NEEDS_FURTHER_REVIEW,
    INACTIVITY,
    OTHER
}
