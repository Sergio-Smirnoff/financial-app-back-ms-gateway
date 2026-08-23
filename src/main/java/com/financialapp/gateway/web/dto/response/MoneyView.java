package com.financialapp.gateway.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record MoneyView(
        @Schema(requiredMode = REQUIRED) String amount,
        @Schema(requiredMode = REQUIRED) String currency,
        MoneyView secondary) {
    public static MoneyView of(String amount, String currency) {
        return new MoneyView(amount, currency, null);
    }

    public static MoneyView of(String amount, String currency, MoneyView secondary) {
        return new MoneyView(amount, currency, secondary);
    }
}
