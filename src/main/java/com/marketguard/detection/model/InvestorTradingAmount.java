package com.marketguard.detection.model;

import java.math.BigDecimal;
import java.util.Objects;

/** Non-negative buy and sell amounts for one KRX investor category. */
public record InvestorTradingAmount(BigDecimal buyAmount, BigDecimal sellAmount) {

    public InvestorTradingAmount {
        Objects.requireNonNull(buyAmount, "buyAmount must not be null");
        Objects.requireNonNull(sellAmount, "sellAmount must not be null");
        if (buyAmount.signum() < 0 || sellAmount.signum() < 0) {
            throw new IllegalArgumentException("trading amounts must not be negative");
        }
    }

    public BigDecimal netAmount() {
        return buyAmount.subtract(sellAmount);
    }
}
