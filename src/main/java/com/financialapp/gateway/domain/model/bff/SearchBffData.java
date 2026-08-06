package com.financialapp.gateway.domain.model.bff;

import com.financialapp.gateway.domain.model.composition.Section;

import java.util.List;
import java.util.Map;

public record SearchBffData(
        Section<List<Map<String, Object>>> movements,
        Section<List<Map<String, Object>>> positions,
        Section<List<Map<String, Object>>> categories
) {}
