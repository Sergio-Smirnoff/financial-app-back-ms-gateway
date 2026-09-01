package com.financialapp.gateway.domain.model.bff;

import com.financialapp.gateway.domain.model.bff.BffDomainModels.*;
import com.financialapp.gateway.domain.model.composition.Section;

import java.util.List;

public record BanksBffData(
        Section<BanksKpis> kpis,
        Section<List<AccountRow>> accounts,
        Section<List<CardRow>> cards,
        Section<List<LoanRow>> loans,
        Section<List<ImportHealthRow>> importHealth,
        Section<List<CompositionSlice>> cashDistribution,
        Section<List<CalendarEntry>> paymentCalendar
) {}
