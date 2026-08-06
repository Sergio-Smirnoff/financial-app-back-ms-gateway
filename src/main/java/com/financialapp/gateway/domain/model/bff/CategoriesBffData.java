package com.financialapp.gateway.domain.model.bff;

import com.financialapp.gateway.domain.model.composition.Section;

import java.util.List;
import java.util.Map;

public record CategoriesBffData(
        Section<Map<String, Object>> kpis,
        Section<List<Map<String, Object>>> budgets,
        Section<Map<String, Object>> selectedTrend,
        Section<List<Map<String, Object>>> rules
) {}
