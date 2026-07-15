package com.marketguard.collector;

/** 호가 잔량의 가용 상태. 0과 미제공/실패를 구분한다. */
public enum OrderbookStatus {
    AVAILABLE,
    NO_DATA,
    UPSTREAM_ERROR,
    PENDING
}
