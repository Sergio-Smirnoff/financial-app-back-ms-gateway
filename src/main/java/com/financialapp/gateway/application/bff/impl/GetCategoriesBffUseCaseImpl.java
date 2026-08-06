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
import com.financialapp.gateway.domain.usecase.bff.GetCategoriesBffUseCase;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class GetCategoriesBffUseCaseImpl implements GetCategoriesBffUseCase {

    private final FinancesGateway finances;
    private final InvestmentsGateway investments;
    private final PageTimeoutBudget budget;
    private final Clock clock;

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

        CompletableFuture<List<Map<String, Object>>> budgetsFuture = finances.fetchBudgets(userId, period);

        CompletableFuture<Section<CategoriesKpis>> kpisSec = applyBudget(
                Section.guard(
                        finances.fetchBudgetPace(userId, period)
                                .thenCombine(fxRateFuture, (paceMap, fx) -> {
                                    BigDecimal spent = parseDecimal(paceMap.get("totalSpent"));
                                    BigDecimal avail = parseDecimal(paceMap.get("totalAvailable"));
                                    int overCount = parseInt(paceMap.get("overBudgetCount"), 0);
                                    BigDecimal pacePct = parseDecimal(paceMap.get("pacePct"));

                                    return new CategoriesKpis(
                                            BffMoneyConverter.convert(spent, Currency.ARS, currencyView, secondary, fx),
                                            BffMoneyConverter.convert(avail, Currency.ARS, currencyView, secondary, fx),
                                            overCount,
                                            pacePct
                                    );
                                }),
                        CategoriesKpis.empty(), clock),
                CategoriesKpis.empty());

        CompletableFuture<Section<List<BudgetRow>>> budgetsSec = applyBudget(
                Section.guard(
                        budgetsFuture.thenCombine(fxRateFuture, (list, fx) -> list.stream().map(b -> {
                            Long catId = parseLong(b.get("categoryId"));
                            String name = String.valueOf(b.getOrDefault("categoryName", b.getOrDefault("name", "")));
                            BigDecimal cap = parseDecimal(b.get("capAmount"));
                            BigDecimal spent = parseDecimal(b.get("spentAmount"));
                            BigDecimal pct = parseDecimal(b.get("spentPct"));
                            BigDecimal threshold = parseDecimal(b.get("alertThresholdPct"));
                            Boolean over = Boolean.TRUE.equals(b.get("overBudget"));
                            return new BudgetRow(catId, name, cap, BffMoneyConverter.convert(spent, Currency.ARS, currencyView, secondary, fx), pct, threshold, over);
                        }).toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<CategoryTrend>> trendSec = applyBudget(
                Section.guard(
                        finances.fetchMonthlyFlow(userId, today.minusMonths(6), today)
                                .thenCombine(fxRateFuture, (flowList, fx) -> {
                                    List<CategoryTrendPoint> points = flowList.stream().map(f -> {
                                        String m = String.valueOf(f.getOrDefault("month", ""));
                                        BigDecimal exp = parseDecimal(f.get("expense"));
                                        return new CategoryTrendPoint(m, BffMoneyConverter.convert(exp, Currency.ARS, currencyView, secondary, fx));
                                    }).toList();
                                    return new CategoryTrend(null, points);
                                }),
                        CategoryTrend.empty(), clock),
                CategoryTrend.empty());

        CompletableFuture<Section<List<RuleRow>>> rulesSec = applyBudget(
                Section.guard(
                        finances.fetchCategorizationRules(userId)
                                .thenApply(list -> list.stream().map(r -> {
                                    Long id = parseLong(r.get("id"));
                                    String matcher = String.valueOf(r.getOrDefault("matcher", ""));
                                    Long catId = parseLong(r.get("categoryId"));
                                    String catName = String.valueOf(r.getOrDefault("categoryName", ""));
                                    Integer priority = parseInt(r.get("priority"), 0);
                                    return new RuleRow(id, matcher, catId, catName, priority);
                                }).toList()),
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

    private static BigDecimal parseDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        try { return new BigDecimal(val.toString()); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private static Long parseLong(Object val) {
        if (val == null) return null;
        try { return Long.parseLong(val.toString()); } catch (Exception e) { return null; }
    }

    private static int parseInt(Object val, int fallback) {
        if (val == null) return fallback;
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return fallback; }
    }
}
