package com.financialapp.gateway.web.dto.response;

import java.time.Instant;

public record SectionResponse<T>(String status, Instant observedAt, T data) {}
