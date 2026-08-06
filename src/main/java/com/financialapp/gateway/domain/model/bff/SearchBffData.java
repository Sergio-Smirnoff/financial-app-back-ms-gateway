package com.financialapp.gateway.domain.model.bff;

import com.financialapp.gateway.domain.model.bff.BffDomainModels.*;
import com.financialapp.gateway.domain.model.composition.Section;

import java.util.List;

public record SearchBffData(
        Section<List<SearchHit>> movements,
        Section<List<SearchHit>> positions,
        Section<List<SearchHit>> categories
) {}
