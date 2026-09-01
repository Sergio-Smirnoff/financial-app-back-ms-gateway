package com.financialapp.gateway.web.dto.response.bff;

import com.financialapp.gateway.web.dto.response.SectionResponse;
import com.financialapp.gateway.web.dto.response.bff.BffWebResponses.*;

import java.util.List;

public record CategoriesBffResponse(
        SectionResponse<CategoriesKpisResponse> kpis,
        SectionResponse<List<BudgetRowResponse>> budgets,
        SectionResponse<CategoryTrendResponse> selectedTrend,
        SectionResponse<List<RuleRowResponse>> rules
) {}
