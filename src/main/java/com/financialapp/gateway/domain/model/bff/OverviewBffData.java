package com.financialapp.gateway.domain.model.bff;

import com.financialapp.gateway.domain.model.bff.BffDomainModels.*;
import com.financialapp.gateway.domain.model.composition.Section;

import java.util.List;

public record OverviewBffData(
        Section<OverviewKpis> kpis,
        Section<NetWorth> netWorth,
        Section<Breakdown> breakdown,
        Section<List<FlowPoint>> flow,
        Section<List<CommittedPoint>> committed,
        Section<List<UpcomingPayment>> upcomingPayments,
        Section<List<CategorySpend>> spendByCategory,
        Section<List<TransactionRow>> latestMovements
) {}
