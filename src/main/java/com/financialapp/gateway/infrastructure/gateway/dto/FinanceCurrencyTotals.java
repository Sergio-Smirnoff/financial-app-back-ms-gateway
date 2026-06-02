package com.financialapp.gateway.infrastructure.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FinanceCurrencyTotals(String totalIncome, String totalExpense, String balance) {}
