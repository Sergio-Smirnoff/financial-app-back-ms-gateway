package com.financialapp.gateway.domain.model.bff;

import com.financialapp.gateway.domain.model.composition.Section;

import java.util.List;
import java.util.Map;

public record InvestmentsBffData(
        Section<Map<String, Object>> marketStrip,
        Section<Map<String, Object>> kpis,
        Section<List<Map<String, Object>>> evolution,
        Section<List<Map<String, Object>>> positions,
        Section<Map<String, Object>> composition,
        Section<List<Map<String, Object>>> recentOperations,
        Section<List<Map<String, Object>>> alerts
) {}
