package com.marketguard.detection.model;

/**
 * 탐지 룰 종류. (Phase 1은 PRICE_SPIKE만 구현, 나머지는 Phase 2 이후 추가)
 */
public enum RuleType {
    PRICE_LIMIT("가격제한폭 도달"),
    PRICE_SPIKE("단기 가격 급변동"),
    VOLUME_SURGE("거래량 급증"),
    ORDERBOOK_IMBALANCE("호가 불균형"),
    INVESTMENT_WARNING("투자경고 종목");

    private final String description;

    RuleType(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
