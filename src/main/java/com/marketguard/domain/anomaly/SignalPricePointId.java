package com.marketguard.domain.anomaly;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SignalPricePointId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long anomalyId;
    private Integer offsetMinutes;
}
