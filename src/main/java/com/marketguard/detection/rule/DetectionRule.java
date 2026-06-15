package com.marketguard.detection.rule;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.RuleType;
import java.util.Optional;

/**
 * 이상거래 탐지 룰. 새 룰은 이 인터페이스만 구현해 빈으로 등록하면
 * RuleEngine이 자동으로 인식한다(개방-폐쇄 원칙: 기존 코드 수정 없이 확장).
 */
public interface DetectionRule {

    RuleType type();

    Optional<Anomaly> evaluate(DetectionContext context);
}
