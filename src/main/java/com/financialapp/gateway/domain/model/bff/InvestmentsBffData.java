package com.financialapp.gateway.domain.model.bff;

import com.financialapp.gateway.domain.model.bff.BffDomainModels.*;
import com.financialapp.gateway.domain.model.composition.Section;

import java.util.List;

public record InvestmentsBffData(
        Section<List<MarketQuote>> marketStrip,
        Section<InvestmentsKpis> kpis,
        Section<List<EvolutionPoint>> evolution,
        Section<List<PositionRow>> positions,
        Section<List<CompositionSlice>> composition,
        Section<List<OperationRow>> recentOperations,
        Section<List<AlertRow>> alerts
) {}
