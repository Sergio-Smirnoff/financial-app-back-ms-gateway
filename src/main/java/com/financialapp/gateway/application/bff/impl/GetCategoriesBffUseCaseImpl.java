package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.FinancesGateway;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.*;
import com.financialapp.gateway.domain.model.bff.CategoriesBffData;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.model.currency.FxRate;
import com.financialapp.gateway.domain.service.BffMoneyConverter;
import com.financialapp.gateway.domain.service.Percentages;
import com.financialapp.gateway.domain.usecase.bff.GetCategoriesBffUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class GetCategoriesBffUseCaseImpl implements GetCategoriesBffUseCase {

    private static final String BUDGETS_SOURCE = "ms-finances budgets";
    private static final String PACE_SOURCE = "ms-finances budget pace";
    private static final String CATEGORIES_SOURCE = "ms-finances categories";
    private static final String FLOW_SOURCE = "ms-finances monthly flow";
    private static final String RULES_SOURCE = "ms-finances categorization rules";

    private final FinancesGateway finances;
    private final InvestmentsGateway investments;
    private final PageTimeoutBudget budget;
    private final Clock clock;

    @Autowired
    public GetCategoriesBffUseCaseImpl(
            FinancesGateway finances, InvestmentsGateway investments, PageTimeoutBudget budget) {
        this(finances, investments, budget, Clock.systemUTC());
    }

    public GetCategoriesBffUseCaseImpl(
            FinancesGateway finances, InvestmentsGateway investments, PageTimeoutBudget budget, Clock clock) {
        this.finances = finances;
        this.investments = investments;
        this.budget = budget != null ? budget : PageTimeoutBudget.fromMillis(5000);
        this.clock = clock;
    }

    @Override
    public CompletableFuture<CategoriesBffData> execute(UserId userId, CurrencyView currencyView, String secondary) {
        LocalDate today = LocalDate.now(clock);
        String period = today.toString().substring(0, 7);

        CompletableFuture<Optional<FxRate>> fxRateFuture = currencyView != CurrencyView.ARS ?
                investments.fetchFxRate(currencyView, today) : CompletableFuture.completedFuture(Optional.empty());

        CompletableFuture<List<DownstreamPayload>> budgetsFuture = finances.fetchBudgets(userId, period)
                .thenApply(rows -> DownstreamPayload.rows(BUDGETS_SOURCE, rows));
        CompletableFuture<List<DownstreamPayload>> categoriesFuture = finances.fetchCategories(userId)
                .thenApply(rows -> DownstreamPayload.rows(CATEGORIES_SOURCE, rows));
        CompletableFuture<List<DownstreamPayload>> paceFuture = finances.fetchBudgetPace(userId, period)
                .thenApply(rows -> DownstreamPayload.rows(PACE_SOURCE, rows));

        CompletableFuture<Section<CategoriesKpis>> kpisSec = applyBudget(
                Section.guard(
                        CompletableFuture.allOf(budgetsFuture, paceFuture, fxRateFuture)
                                .thenApply(v -> {
                                    Optional<FxRate> fx = fxRateFuture.join();
                                    List<DownstreamPayload> pace = paceFuture.join();
                                    BigDecimal spent = pace.stream().map(p -> p.decimalOrZero("spent")).reduce(BigDecimal.ZERO, BigDecimal::add);
                                    BigDecimal available = pace.stream().map(p -> p.decimalOrZero("remaining")).reduce(BigDecimal.ZERO, BigDecimal::add);
                                    int overCount = (int) pace.stream().filter(p -> p.flagOr("overBudget", false)).count();
                                    BigDecimal capTotal = budgetsFuture.join().stream().map(b -> b.decimalOrZero("amount")).reduce(BigDecimal.ZERO, BigDecimal::add);
                                    return new CategoriesKpis(
                                            BffMoneyConverter.convert(spent, Currency.ARS, currencyView, secondary, fx),
                                            BffMoneyConverter.convert(available, Currency.ARS, currencyView, secondary, fx),
                                            overCount,
                                            Percentages.percentOf(spent, capTotal));
                                }),
                        CategoriesKpis.empty(), clock),
                CategoriesKpis.empty());

        CompletableFuture<Section<List<BudgetRow>>> budgetsSec = applyBudget(
                Section.guard(
                        CompletableFuture.allOf(budgetsFuture, paceFuture, categoriesFuture, fxRateFuture)
                                .thenApply(v -> {
                                    Optional<FxRate> fx = fxRateFuture.join();
                                    Map<Long, DownstreamPayload> paceByCategory = byCategoryId(paceFuture.join());
                                    Map<Long, DownstreamPayload> budgetByCategory = byCategoryId(budgetsFuture.join());
                                    List<BudgetRow> rows = new ArrayList<>();
                                    for (DownstreamPayload category : categoriesFuture.join()) {
                                        Optional<Long> categoryId = category.optionalLong("id");
                                        if (categoryId.isEmpty()) {
                                            continue;
                                        }
                                        String name = category.textOr("name", "");
                                        rows.add(toBudgetRow(categoryId.get(), null, name,
                                                budgetByCategory.remove(categoryId.get()), paceByCategory, currencyView, secondary, fx));
                                        for (DownstreamPayload subcategory : category.listOrEmpty("subcategories")) {
                                            Optional<Long> subcategoryId = subcategory.optionalLong("id");
                                            if (subcategoryId.isEmpty()) {
                                                continue;
                                            }
                                            rows.add(toBudgetRow(subcategoryId.get(), categoryId.get(),
                                                    CategoryTree.childLabel(name, subcategory.textOr("name", "")),
                                                    budgetByCategory.remove(subcategoryId.get()), paceByCategory, currencyView, secondary, fx));
                                        }
                                    }
                                    for (Map.Entry<Long, DownstreamPayload> orphan : budgetByCategory.entrySet()) {
                                        DownstreamPayload orphanBudget = orphan.getValue();
                                        String name = orphanBudget.optionalText("categoryName")
                                                .or(() -> orphanBudget.optionalText("name"))
                                                .orElse("");
                                        rows.add(toBudgetRow(orphan.getKey(), null, name, orphanBudget, paceByCategory, currencyView, secondary, fx));
                                    }
                                    return List.copyOf(rows);
                                }),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<CategoryTrend>> trendSec = applyBudget(
                Section.guard(
                        finances.fetchMonthlyFlow(userId, today.minusMonths(6), today)
                                .thenCombine(fxRateFuture, (rows, fx) -> new CategoryTrend(null,
                                        DownstreamPayload.rows(FLOW_SOURCE, rows).stream()
                                                .map(month -> new CategoryTrendPoint(month.textOr("month", ""),
                                                        BffMoneyConverter.convert(month.decimalOrZero("expense"), Currency.ARS, currencyView, secondary, fx)))
                                                .toList())),
                        CategoryTrend.empty(), clock),
                CategoryTrend.empty());

        CompletableFuture<Section<List<RuleRow>>> rulesSec = applyBudget(
                Section.guard(
                        finances.fetchCategorizationRules(userId)
                                .thenApply(rows -> DownstreamPayload.rows(RULES_SOURCE, rows).stream()
                                        .map(rule -> new RuleRow(
                                                rule.longValue("id"),
                                                rule.text("pattern"),
                                                rule.optionalLong("categoryId").orElse(null),
                                                rule.textOr("categoryName", ""),
                                                null))
                                        .toList()),
                        List.of(), clock),
                List.of());

        return CompletableFuture.allOf(kpisSec, budgetsSec, trendSec, rulesSec)
                .thenApply(v -> new CategoriesBffData(
                        kpisSec.join(), budgetsSec.join(), trendSec.join(), rulesSec.join()));
    }

    private <T> CompletableFuture<Section<T>> applyBudget(CompletableFuture<Section<T>> sectionFuture, T fallback) {
        return sectionFuture.completeOnTimeout(
                Section.unavailable(fallback, ObservedAt.now(clock)),
                budget.total().toMillis(),
                TimeUnit.MILLISECONDS);
    }

    private static Map<Long, DownstreamPayload> byCategoryId(List<DownstreamPayload> rows) {
        Map<Long, DownstreamPayload> byId = new HashMap<>();
        for (DownstreamPayload row : rows) {
            row.optionalLong("categoryId").ifPresent(id -> byId.putIfAbsent(id, row));
        }
        return byId;
    }

    private BudgetRow toBudgetRow(
            Long categoryId, Long parentId, String name, DownstreamPayload budgetRow,
            Map<Long, DownstreamPayload> paceByCategory,
            CurrencyView currencyView, String secondary, Optional<FxRate> fx) {
        DownstreamPayload cap = budgetRow != null ? budgetRow : new DownstreamPayload(BUDGETS_SOURCE, Map.of());
        DownstreamPayload pace = paceByCategory.getOrDefault(categoryId, new DownstreamPayload(PACE_SOURCE, Map.of()));
        return new BudgetRow(categoryId, parentId, name,
                cap.decimalOrZero("amount"),
                BffMoneyConverter.convert(pace.decimalOrZero("spent"), Currency.ARS, currencyView, secondary, fx),
                pace.decimalOrZero("pctUsed"),
                cap.decimalOrZero("alertThresholdPct"),
                pace.flagOr("overBudget", false));
    }
}
