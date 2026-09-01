package com.financialapp.gateway.domain.model.bff;

import java.time.LocalDate;

public record UpcomingPaymentView(
        Long id,
        String type,
        String description,
        String amount,
        String currency,
        LocalDate dueDate,
        int installmentNumber,
        int totalInstallments,
        boolean paid) {}
