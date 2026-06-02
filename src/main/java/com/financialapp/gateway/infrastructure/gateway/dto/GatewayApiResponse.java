package com.financialapp.gateway.infrastructure.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Foreign envelope of downstream services: { success, message, data, errors, timestamp }. Only data is read. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GatewayApiResponse<T>(T data) {}
