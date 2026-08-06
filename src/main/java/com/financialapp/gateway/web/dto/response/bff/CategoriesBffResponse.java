package com.financialapp.gateway.web.dto.response.bff;

import com.financialapp.gateway.web.dto.response.SectionResponse;

import java.util.List;
import java.util.Map;

public record CategoriesBffResponse(
        SectionResponse<Map<String, Object>> kpis,
        SectionResponse<List<Map<String, Object>>> budgets,
        SectionResponse<Map<String, Object>> selectedTrend,
        SectionResponse<List<Map<String, Object>>> rules
) {}
