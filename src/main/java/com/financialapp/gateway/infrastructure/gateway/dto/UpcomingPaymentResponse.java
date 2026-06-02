package com.financialapp.gateway.infrastructure.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpcomingPaymentResponse(
        Long id,
        String type,
        String description,
        String amount,
        String currency,
        LocalDate dueDate,
        int installmentNumber,
        int totalInstallments,
        boolean paid) {}
