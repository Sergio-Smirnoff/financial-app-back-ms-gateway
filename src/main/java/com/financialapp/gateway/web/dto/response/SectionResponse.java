package com.financialapp.gateway.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record SectionResponse<T>(
        @Schema(requiredMode = REQUIRED) String status,
        @Schema(requiredMode = REQUIRED) Instant observedAt,
        T data) {}
