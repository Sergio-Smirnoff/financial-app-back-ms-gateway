package com.financialapp.gateway.infrastructure.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LoanResponse(
        Long id,
        String name,
        String currency,
        String principal,
        int totalInstallments,
        int remainingInstallments,
        boolean active) {}
