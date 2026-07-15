package com.marketguard.detection.model;

/** 관측된 시장 신호의 방향. 방향을 판단할 수 없거나 해당하지 않으면 NONE을 사용한다. */
public enum Direction {
    UP,
    DOWN,
    BUY,
    SELL,
    NONE
}
