package com.financialapp.gateway.domain.model.bff;

import com.financialapp.gateway.domain.model.bff.BffDomainModels.*;
import com.financialapp.gateway.domain.model.composition.Section;

import java.util.List;

public record ImportsBffData(
        Section<ActiveRun> activeRun,
        Section<List<ImportRunRow>> history,
        Section<List<ReconciliationRow>> reconciliation
) {}
