package com.marketguard.detection.engine;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.rule.DetectionRule;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 등록된 모든 탐지 룰을 한 종목 컨텍스트에 대해 평가한다.
 * 룰 목록은 스프링이 DetectionRule 빈을 모두 주입해 채운다.
 */
@Slf4j
@Component
public class RuleEngine {

    private final List<DetectionRule> rules;

    public RuleEngine(List<DetectionRule> rules) {
        this.rules = rules;
        log.info("탐지 룰 {}개 로드됨: {}", rules.size(),
                rules.stream().map(rule -> rule.type().name()).toList());
    }

    public List<Anomaly> evaluate(DetectionContext context) {
        return rules.stream()
                .map(rule -> rule.evaluate(context))
                .flatMap(Optional::stream)
                .toList();
    }
}
