package com.marketguard.detection.rule;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.RuleType;
import java.util.Optional;

/**
 * 이상거래 탐지 룰. 프레임워크 바깥 구성 계층이 구현체를 RuleEngine에 조립한다.
 */
public interface DetectionRule {

    RuleType type();

    Optional<Anomaly> evaluate(DetectionContext context);
}
