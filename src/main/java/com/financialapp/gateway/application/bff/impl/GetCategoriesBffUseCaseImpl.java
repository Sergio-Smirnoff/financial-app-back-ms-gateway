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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class GetCategoriesBffUseCaseImpl implements GetCategoriesBffUseCase {

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

        CompletableFuture<List<Map<String, Object>>> budgetsFuture = finances.fetchBudgets(userId, period);
        CompletableFuture<List<Map<String, Object>>> categoriesFuture = finances.fetchCategories(userId);
        CompletableFuture<List<Map<String, Object>>> paceFuture = finances.fetchBudgetPace(userId, period);

        CompletableFuture<Section<CategoriesKpis>> kpisSec = applyBudget(
                Section.guard(
                        CompletableFuture.allOf(budgetsFuture, paceFuture, fxRateFuture)
                                .thenApply(v -> {
                                    Optional<FxRate> fx = fxRateFuture.join();
                                    List<Map<String, Object>> paceList = paceFuture.join();
                                    BigDecimal spent = paceList.stream().map(p -> parseDecimal(p.get("spent"))).reduce(BigDecimal.ZERO, BigDecimal::add);
                                    BigDecimal avail = paceList.stream().map(p -> parseDecimal(p.get("remaining"))).reduce(BigDecimal.ZERO, BigDecimal::add);
                                    int overCount = (int) paceList.stream().filter(p -> Boolean.TRUE.equals(p.get("overBudget"))).count();
                                    BigDecimal capTotal = budgetsFuture.join().stream().map(b -> parseDecimal(b.get("amount"))).reduce(BigDecimal.ZERO, BigDecimal::add);
                                    BigDecimal pacePct = capTotal.compareTo(BigDecimal.ZERO) > 0
                                             ? spent.multiply(new BigDecimal("100")).divide(capTotal, 2, RoundingMode.HALF_EVEN)
                                             : BigDecimal.ZERO;

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
                        CompletableFuture.allOf(budgetsFuture, paceFuture, categoriesFuture, fxRateFuture)
                                .thenApply(v -> {
                                    Optional<FxRate> fx = fxRateFuture.join();
                                    Map<Long, Map<String, Object>> paceByCategory = paceFuture.join().stream()
                                            .filter(p -> parseLong(p.get("categoryId")) != null)
                                            .collect(Collectors.toMap(p -> parseLong(p.get("categoryId")), p -> p, (a, b) -> a));
                                    Map<Long, Map<String, Object>> budgetByCategory = budgetsFuture.join().stream()
                                            .filter(b -> parseLong(b.get("categoryId")) != null)
                                            .collect(Collectors.toMap(b -> parseLong(b.get("categoryId")), b -> b, (a, b) -> a));

                                    List<BudgetRow> rows = new ArrayList<>();
                                    for (Map<String, Object> category : categoriesFuture.join()) {
                                        Long catId = parseLong(category.get("id"));
                                        if (catId == null) continue;
                                        String name = String.valueOf(category.getOrDefault("name", ""));
                                        rows.add(toBudgetRow(catId, null, name, budgetByCategory.remove(catId), paceByCategory, currencyView, secondary, fx));

                                        Object subsObj = category.get("subcategories");
                                        if (subsObj instanceof List<?> subs) {
                                            for (Object subObj : subs) {
                                                if (!(subObj instanceof Map<?, ?> sub)) continue;
                                                Long subId = parseLong(sub.get("id"));
                                                if (subId == null) continue;
                                                Object rawSubName = sub.get("name");
                                                String subName = name + " / " + (rawSubName != null ? rawSubName : "");
                                                rows.add(toBudgetRow(subId, catId, subName, budgetByCategory.remove(subId), paceByCategory, currencyView, secondary, fx));
                                            }
                                        }
                                    }
                                    for (Map.Entry<Long, Map<String, Object>> orphan : budgetByCategory.entrySet()) {
                                        Map<String, Object> b = orphan.getValue();
                                        String name = String.valueOf(b.getOrDefault("categoryName", b.getOrDefault("name", "")));
                                        rows.add(toBudgetRow(orphan.getKey(), null, name, b, paceByCategory, currencyView, secondary, fx));
                                    }
                                    return List.copyOf(rows);
                                }),
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

    private BudgetRow toBudgetRow(
            Long categoryId, Long parentId, String name, Map<String, Object> budget,
            Map<Long, Map<String, Object>> paceByCategory,
            CurrencyView currencyView, String secondary, Optional<FxRate> fx) {
        Map<String, Object> budgetRow = budget != null ? budget : Map.of();
        Map<String, Object> pace = paceByCategory.getOrDefault(categoryId, Map.of());
        BigDecimal cap = parseDecimal(budgetRow.get("amount"));
        BigDecimal threshold = parseDecimal(budgetRow.get("alertThresholdPct"));
        BigDecimal spent = parseDecimal(pace.get("spent"));
        BigDecimal pct = parseDecimal(pace.get("pctUsed"));
        Boolean over = Boolean.TRUE.equals(pace.get("overBudget"));
        return new BudgetRow(categoryId, parentId, name, cap,
                BffMoneyConverter.convert(spent, Currency.ARS, currencyView, secondary, fx),
                pct, threshold, over);
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
