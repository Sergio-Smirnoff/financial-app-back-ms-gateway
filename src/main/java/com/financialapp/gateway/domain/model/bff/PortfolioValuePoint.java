package com.financialapp.gateway.domain.model.bff;

import java.time.LocalDate;

public record PortfolioValuePoint(LocalDate date, CurrencyAmounts marketValue) {

    public PortfolioValuePoint {
        if (date == null || marketValue == null) {
            throw new IllegalArgumentException("date and marketValue required");
        }
    }
}
