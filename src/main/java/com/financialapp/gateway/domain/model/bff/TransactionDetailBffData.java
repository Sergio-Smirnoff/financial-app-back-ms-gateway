package com.financialapp.gateway.domain.model.bff;

import com.financialapp.gateway.domain.model.composition.Section;

import java.util.Map;

public record TransactionDetailBffData(
        Section<Map<String, Object>> detail
) {}
