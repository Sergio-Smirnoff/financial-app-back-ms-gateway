package com.financialapp.gateway.infrastructure.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Foreign DTO: per-currency totals from ms-finances /transactions/summary (map value). Money as String. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FinanceCurrencyTotals(String totalIncome, String totalExpense, String balance) {}
