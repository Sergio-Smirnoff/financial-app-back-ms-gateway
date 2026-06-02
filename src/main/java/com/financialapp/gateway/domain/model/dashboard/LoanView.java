package com.financialapp.gateway.domain.model.dashboard;

/** An active loan as shown on the dashboard. Money as String. */
public record LoanView(Long id, String name, String currency, String principal, boolean active) {}
