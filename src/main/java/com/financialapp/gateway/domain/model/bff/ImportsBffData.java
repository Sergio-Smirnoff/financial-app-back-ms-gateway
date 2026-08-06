package com.financialapp.gateway.domain.model.bff;

import com.financialapp.gateway.domain.model.composition.Section;

import java.util.List;
import java.util.Map;

public record ImportsBffData(
        Section<Map<String, Object>> activeRun,
        Section<List<Map<String, Object>>> history,
        Section<Map<String, Object>> reconciliation
) {}
