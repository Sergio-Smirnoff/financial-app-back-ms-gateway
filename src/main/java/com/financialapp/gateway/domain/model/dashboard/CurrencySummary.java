package com.financialapp.gateway.domain.model.dashboard;

/** Per-currency income/expense/balance totals for the dashboard. Money as String. */
public record CurrencySummary(String currency, String totalIncome, String totalExpense, String balance) {}
