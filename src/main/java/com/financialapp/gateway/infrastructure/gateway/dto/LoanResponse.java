package com.financialapp.gateway.infrastructure.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Foreign DTO: a loan from ms-banks /loans. Only the fields the dashboard needs. Money as String. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LoanResponse(Long id, String name, String currency, String principal, boolean active) {}
