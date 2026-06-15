package com.marketguard.detection.model;

import com.marketguard.domain.marketdata.PriceSnapshot;
import java.util.List;

/**
 * 한 종목에 대한 탐지 시점의 입력 데이터.
 * current: 방금 수집한 스냅샷, recent: 비교용 직전 스냅샷들(최신순).
 */
public record DetectionContext(
        PriceSnapshot current,
        List<PriceSnapshot> recent
) {
}
