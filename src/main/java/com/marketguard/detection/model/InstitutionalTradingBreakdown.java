package com.marketguard.detection.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** The seven KRX institutional investor categories returned by Toss Open API. */
public record InstitutionalTradingBreakdown(
        InvestorTradingAmount financialInvestment,
        InvestorTradingAmount insurance,
        InvestorTradingAmount trust,
        InvestorTradingAmount privateEquityFund,
        InvestorTradingAmount bank,
        InvestorTradingAmount otherFinancialInstitution,
        InvestorTradingAmount pensionFund
) {
    public InstitutionalTradingBreakdown {
        Objects.requireNonNull(financialInvestment, "financialInvestment must not be null");
        Objects.requireNonNull(insurance, "insurance must not be null");
        Objects.requireNonNull(trust, "trust must not be null");
        Objects.requireNonNull(privateEquityFund, "privateEquityFund must not be null");
        Objects.requireNonNull(bank, "bank must not be null");
        Objects.requireNonNull(otherFinancialInstitution, "otherFinancialInstitution must not be null");
        Objects.requireNonNull(pensionFund, "pensionFund must not be null");
    }

    public BigDecimal buyTotal() {
        return categories().stream()
                .map(InvestorTradingAmount::buyAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal sellTotal() {
        return categories().stream()
                .map(InvestorTradingAmount::sellAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<InvestorTradingAmount> categories() {
        return List.of(financialInvestment, insurance, trust, privateEquityFund, bank,
                otherFinancialInstitution, pensionFund);
    }
}
