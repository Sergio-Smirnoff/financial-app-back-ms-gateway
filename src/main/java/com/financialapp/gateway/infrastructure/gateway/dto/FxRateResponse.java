package com.financialapp.gateway.infrastructure.gateway.dto;

import java.time.LocalDate;

public record FxRateResponse(LocalDate date, String view, String buy, String sell, String source) {}
