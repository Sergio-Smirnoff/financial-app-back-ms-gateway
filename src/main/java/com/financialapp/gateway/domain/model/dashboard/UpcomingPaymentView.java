package com.financialapp.gateway.domain.model.dashboard;

import java.time.LocalDate;

/** An upcoming loan/card installment as shown on the dashboard. Money as String. */
public record UpcomingPaymentView(
        Long id,
        String type,
        String description,
        String amount,
        String currency,
        LocalDate dueDate) {}
