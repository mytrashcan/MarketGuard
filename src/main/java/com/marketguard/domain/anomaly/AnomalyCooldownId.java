package com.marketguard.domain.anomaly;

import com.marketguard.detection.model.RuleType;
import java.io.Serializable;
import java.io.Serial;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AnomalyCooldownId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String stockCode;
    private RuleType ruleType;
}
