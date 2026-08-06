package com.financialapp.gateway.web.dto.response.bff;

import com.financialapp.gateway.web.dto.response.SectionResponse;

import java.util.Map;

public record TransactionsBffResponse(
        SectionResponse<Map<String, Object>> summary,
        SectionResponse<Map<String, Object>> page,
        SectionResponse<Map<String, Object>> filterOptions,
        SectionResponse<Map<String, Object>> uncategorised
) {}
