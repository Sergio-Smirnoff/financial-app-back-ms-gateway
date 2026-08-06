package com.financialapp.gateway.web.dto.response.bff;

import com.financialapp.gateway.web.dto.response.SectionResponse;

import java.util.List;
import java.util.Map;

public record InvestmentsBffResponse(
        SectionResponse<Map<String, Object>> marketStrip,
        SectionResponse<Map<String, Object>> kpis,
        SectionResponse<List<Map<String, Object>>> evolution,
        SectionResponse<List<Map<String, Object>>> positions,
        SectionResponse<Map<String, Object>> composition,
        SectionResponse<List<Map<String, Object>>> recentOperations,
        SectionResponse<List<Map<String, Object>>> alerts
) {}
