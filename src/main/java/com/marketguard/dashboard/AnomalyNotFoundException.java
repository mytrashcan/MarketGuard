package com.marketguard.dashboard;

/** 요청한 이상 신호 기록이 없을 때 발생한다. 사건(case) 미존재와 구분해 404 코드를 정확히 돌려준다. */
public class AnomalyNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public AnomalyNotFoundException(Long id) {
        super("anomaly not found: " + id);
    }
}
