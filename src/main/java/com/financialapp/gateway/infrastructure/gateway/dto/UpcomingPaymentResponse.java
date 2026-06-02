package com.financialapp.gateway.infrastructure.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

/** Foreign DTO: an upcoming installment from ms-banks /upcoming-payments. Money as String. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpcomingPaymentResponse(
        Long id,
        String type,
        String description,
        String amount,
        String currency,
        LocalDate dueDate) {}
