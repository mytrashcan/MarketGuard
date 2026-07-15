package com.marketguard.detection.context;

import java.time.Instant;

/** 업종, 시장지수, 감시 유니버스 순의 상대 움직임 제공자를 위한 코어 포트. */
public interface RelativeMovementProvider {
    RelativeMovement compare(String stockCode, Instant observedAt);
}
