package com.financialapp.gateway.domain.model.bff;

import com.financialapp.gateway.domain.model.composition.Section;

import java.util.List;
import java.util.Map;

public record TransactionsBffData(
        Section<Map<String, Object>> summary,
        Section<Map<String, Object>> page,
        Section<Map<String, Object>> filterOptions,
        Section<Map<String, Object>> uncategorised
) {}
