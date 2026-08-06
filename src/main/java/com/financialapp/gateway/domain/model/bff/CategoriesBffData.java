package com.financialapp.gateway.domain.model.bff;

import com.financialapp.gateway.domain.model.bff.BffDomainModels.*;
import com.financialapp.gateway.domain.model.composition.Section;

import java.util.List;

public record CategoriesBffData(
        Section<CategoriesKpis> kpis,
        Section<List<BudgetRow>> budgets,
        Section<CategoryTrend> selectedTrend,
        Section<List<RuleRow>> rules
) {}
