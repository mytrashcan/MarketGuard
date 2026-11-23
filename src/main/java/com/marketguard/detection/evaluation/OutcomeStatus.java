package com.marketguard.detection.evaluation;

/** 신호 이후 가격 결과를 측정했는지. 측정하지 못한 이유를 구분해 표본 편향을 드러낸다. */
public enum OutcomeStatus {
    /** 신호 시점과 측정 시점의 가격이 모두 있어 변동률을 계산했다. */
    MEASURED,
    /** 측정 구간에 시세 스냅샷이 없었다(장 마감, 스캔 중단, 보존 기간 경과). */
    NO_PRICE_DATA,
    /** 시장 전체 신호처럼 종목 가격으로 결과를 잴 수 없는 신호. */
    NOT_APPLICABLE
}
