package com.financialapp.gateway.domain.model.bff;

public record LoanView(
        Long id,
        String name,
        String currency,
        String principal,
        int totalInstallments,
        int remainingInstallments,
        boolean active) {}
