package com.marketguard.detection.model;

/**
 * 지원하는 탐지 룰 종류.
 */
public enum RuleType {
    PRICE_LIMIT("가격제한폭 도달"),
    PRICE_SPIKE("단기 가격 급변동"),
    VOLUME_SURGE("거래량 급증"),
    ORDERBOOK_IMBALANCE("호가 불균형"),
    INVESTMENT_WARNING("투자경고 종목"),
    PRICE_VOLUME_SURGE("가격·거래량 동시 급증"),
    INSTITUTIONAL_NET_BUY_SURGE("기관 순매수 급증"),
    INSTITUTIONAL_NET_SELL_SURGE("기관 순매도 급증");

    private final String description;

    RuleType(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
