package com.financialapp.gateway.domain.model.bff;

import com.financialapp.gateway.domain.model.composition.Section;

import java.util.List;
import java.util.Map;

public record OverviewBffData(
        Section<Map<String, Object>> kpis,
        Section<Map<String, Object>> netWorth,
        Section<Map<String, Object>> breakdown,
        Section<List<Map<String, Object>>> flow,
        Section<List<Map<String, Object>>> committed,
        Section<List<Map<String, Object>>> upcomingPayments,
        Section<List<Map<String, Object>>> spendByCategory,
        Section<List<Map<String, Object>>> latestMovements
) {}
