package com.marketguard.collector.client;

import com.marketguard.detection.model.InstitutionalTradingBreakdown;
import com.marketguard.detection.model.InstitutionalTradingRecord;
import com.marketguard.detection.model.InvestorTradingAmount;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Official Toss investor-trading response mapped to the framework-free detection model. */
record InvestorTradingResponse(Result result) {

    List<InstitutionalTradingRecord> toDomain(String marketSymbol) {
        if (result == null || result.records() == null) {
            return List.of();
        }
        return result.records().stream()
                .map(record -> record.toDomain(marketSymbol))
                .toList();
    }

    record Result(List<Record> records, LocalDate nextUntil) {
    }

    record Record(
            LocalDate date,
            Instant updatedAt,
            InvestorTradingAmountResponse institution
    ) {
        InstitutionalTradingRecord toDomain(String marketSymbol) {
            return new InstitutionalTradingRecord(
                    marketSymbol,
                    date,
                    updatedAt,
                    institution.toDomain(),
                    institution.breakdown().toDomain());
        }
    }

    record TradingAmountResponse(BigDecimal buyAmount, BigDecimal sellAmount) {
        InvestorTradingAmount toDomain() {
            return new InvestorTradingAmount(buyAmount, sellAmount);
        }
    }

    record InvestorTradingAmountResponse(
            BigDecimal buyAmount,
            BigDecimal sellAmount,
            BreakdownResponse breakdown
    ) {
        InvestorTradingAmount toDomain() {
            return new InvestorTradingAmount(buyAmount, sellAmount);
        }
    }

    record BreakdownResponse(
            TradingAmountResponse financialInvestment,
            TradingAmountResponse insurance,
            TradingAmountResponse trust,
            TradingAmountResponse privateEquityFund,
            TradingAmountResponse bank,
            TradingAmountResponse otherFinancialInstitution,
            TradingAmountResponse pensionFund
    ) {
        InstitutionalTradingBreakdown toDomain() {
            return new InstitutionalTradingBreakdown(
                    financialInvestment.toDomain(),
                    insurance.toDomain(),
                    trust.toDomain(),
                    privateEquityFund.toDomain(),
                    bank.toDomain(),
                    otherFinancialInstitution.toDomain(),
                    pensionFund.toDomain());
        }
    }
}
