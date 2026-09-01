package com.financialapp.gateway.infrastructure.gateway.dto;

public record UserPreferencesResponse(
        int maxIdleMinutes,
        String timezone,
        String primaryCurrency,
        String secondaryCurrency,
        String numberFormat,
        Integer decimals,
        Boolean colorForAmounts) {}
