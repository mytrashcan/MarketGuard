package com.marketguard.detection.model;

import java.math.BigDecimal;

/**
 * 가격제한폭(상·하한가) 값 객체.
 */
public record PriceLimit(BigDecimal upperLimit, BigDecimal lowerLimit) {
}
