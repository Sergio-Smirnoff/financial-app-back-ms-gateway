package com.financialapp.gateway.web.dto.response.bff;

import com.financialapp.gateway.web.dto.response.SectionResponse;

import java.util.List;
import java.util.Map;

public record OverviewBffResponse(
        SectionResponse<Map<String, Object>> kpis,
        SectionResponse<Map<String, Object>> netWorth,
        SectionResponse<Map<String, Object>> breakdown,
        SectionResponse<List<Map<String, Object>>> flow,
        SectionResponse<List<Map<String, Object>>> committed,
        SectionResponse<List<Map<String, Object>>> upcomingPayments,
        SectionResponse<List<Map<String, Object>>> spendByCategory,
        SectionResponse<List<Map<String, Object>>> latestMovements
) {}
