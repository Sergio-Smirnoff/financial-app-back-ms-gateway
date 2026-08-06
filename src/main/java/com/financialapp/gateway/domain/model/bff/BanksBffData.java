package com.financialapp.gateway.domain.model.bff;

import com.financialapp.gateway.domain.model.composition.Section;

import java.util.List;
import java.util.Map;

public record BanksBffData(
        Section<Map<String, Object>> kpis,
        Section<List<Map<String, Object>>> accounts,
        Section<List<Map<String, Object>>> cards,
        Section<List<Map<String, Object>>> loans,
        Section<List<Map<String, Object>>> importHealth,
        Section<List<Map<String, Object>>> cashDistribution,
        Section<List<Map<String, Object>>> paymentCalendar
) {}
