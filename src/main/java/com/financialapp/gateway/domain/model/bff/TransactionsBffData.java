package com.financialapp.gateway.domain.model.bff;

import com.financialapp.gateway.domain.model.bff.BffDomainModels.*;
import com.financialapp.gateway.domain.model.composition.Section;

public record TransactionsBffData(
        Section<TransactionsSummary> summary,
        Section<TransactionsPage> page,
        Section<FilterOptions> filterOptions,
        Section<UncategorisedSummary> uncategorised
) {}
