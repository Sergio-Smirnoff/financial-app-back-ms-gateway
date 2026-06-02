package com.financialapp.gateway.domain.model.dashboard;

import java.time.LocalDate;

public record UpcomingPaymentView(
        Long id,
        String type,
        String description,
        String amount,
        String currency,
        LocalDate dueDate) {}
