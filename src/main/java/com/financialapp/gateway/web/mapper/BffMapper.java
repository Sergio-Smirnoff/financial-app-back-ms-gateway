package com.financialapp.gateway.web.mapper;

import com.financialapp.gateway.domain.model.bff.BffDomainModels.*;
import com.financialapp.gateway.domain.model.bff.MoneyFigure;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.web.dto.response.MoneyView;
import com.financialapp.gateway.web.dto.response.SectionResponse;
import com.financialapp.gateway.web.dto.response.bff.BffWebResponses.*;

import java.math.RoundingMode;
import java.util.List;
import java.util.function.Function;

public final class BffMapper {

    private BffMapper() {}

    public static <T> SectionResponse<T> toSectionResponse(Section<T> section) {
        if (section == null) {
            return null;
        }
        return new SectionResponse<>(section.status().name(), section.observedAt().value(), section.data());
    }

    public static <T, R> SectionResponse<R> toSectionResponse(Section<T> section, Function<T, R> mapper) {
        if (section == null) {
            return null;
        }
        R mappedData = section.data() != null ? mapper.apply(section.data()) : null;
        return new SectionResponse<>(section.status().name(), section.observedAt().value(), mappedData);
    }

    public static MoneyView toMoneyView(MoneyFigure figure) {
        if (figure == null) {
            return null;
        }
        String amountStr = figure.amount() != null ? figure.amount().setScale(2, RoundingMode.HALF_EVEN).toPlainString() : "0.00";
        String currencyStr = figure.currency() != null ? figure.currency().code() : "ARS";
        MoneyView secondary = figure.secondary() != null ? toMoneyView(figure.secondary()) : null;
        return new MoneyView(amountStr, currencyStr, secondary);
    }

    public static OverviewKpisResponse toOverviewKpisResponse(OverviewKpis kpis) {
        if (kpis == null) return null;
        return new OverviewKpisResponse(
                toMoneyView(kpis.cash()),
                toMoneyView(kpis.income()),
                toMoneyView(kpis.expense()),
                toMoneyView(kpis.committed())
        );
    }

    public static NetWorthResponse toNetWorthResponse(NetWorth netWorth) {
        if (netWorth == null) return null;
        List<NetWorthPointResponse> series = netWorth.series() == null ? List.of() :
                netWorth.series().stream().map(p -> new NetWorthPointResponse(p.date(), toMoneyView(p.value()))).toList();
        NetWorthDeltaResponse delta = netWorth.delta() == null ? null :
                new NetWorthDeltaResponse(toMoneyView(netWorth.delta().amount()), netWorth.delta().pct());
        return new NetWorthResponse(series, delta, netWorth.allTimeHigh());
    }

    public static BreakdownResponse toBreakdownResponse(Breakdown b) {
        if (b == null) return null;
        return new BreakdownResponse(
                toMoneyView(b.investments()),
                toMoneyView(b.cash()),
                toMoneyView(b.debt()),
                toMoneyView(b.savings())
        );
    }

    public static TransactionRowResponse toTransactionRowResponse(TransactionRow r) {
        if (r == null) return null;
        return new TransactionRowResponse(
                r.id(), r.date(), r.description(), r.accountCbu(), r.accountAlias(),
                r.categoryId(), r.categoryName(), r.method(), r.note(),
                toMoneyView(r.amount()), r.direction() != null ? r.direction().name() : "OUT"
        );
    }

    public static FlowPointResponse toFlowPointResponse(FlowPoint f) {
        if (f == null) return null;
        return new FlowPointResponse(f.month(), toMoneyView(f.income()), toMoneyView(f.expense()));
    }

    public static CommittedPointResponse toCommittedPointResponse(CommittedPoint c) {
        if (c == null) return null;
        return new CommittedPointResponse(c.month(), toMoneyView(c.amount()));
    }

    public static UpcomingPaymentResponse toUpcomingPaymentResponse(UpcomingPayment u) {
        if (u == null) return null;
        return new UpcomingPaymentResponse(u.id(), u.label(), u.dueDate(), toMoneyView(u.amount()), u.kind());
    }

    public static CategorySpendResponse toCategorySpendResponse(CategorySpend c) {
        if (c == null) return null;
        return new CategorySpendResponse(c.categoryId(), c.name(), toMoneyView(c.amount()), c.pct());
    }

    public static BanksKpisResponse toBanksKpisResponse(BanksKpis k) {
        if (k == null) return null;
        return new BanksKpisResponse(toMoneyView(k.totalCash()), toMoneyView(k.cardDebt()), toMoneyView(k.loanBalance()), k.accountCount());
    }

    public static AccountRowResponse toAccountRowResponse(AccountRow a) {
        if (a == null) return null;
        return new AccountRowResponse(a.cbu(), a.alias(), a.bankName(), a.type(), toMoneyView(a.balance()));
    }

    public static CardRowResponse toCardRowResponse(CardRow c) {
        if (c == null) return null;
        return new CardRowResponse(c.cardNumber(), c.brand(), c.alias(), c.limit(), toMoneyView(c.used()), c.usedPct(), c.closingDate(), c.dueDate());
    }

    public static LoanRowResponse toLoanRowResponse(LoanRow l) {
        if (l == null) return null;
        return new LoanRowResponse(l.id(), l.label(), l.principal(), toMoneyView(l.outstanding()), l.nextInstallmentDate(), l.installmentsPaid(), l.installmentsTotal());
    }

    public static ImportHealthRowResponse toImportHealthRowResponse(ImportHealthRow h) {
        if (h == null) return null;
        return new ImportHealthRowResponse(h.cbu(), h.alias(), h.lastImportAt(), h.daysSince(), h.status() != null ? h.status().name() : "NEVER");
    }

    public static CompositionSliceResponse toCompositionSliceResponse(CompositionSlice c) {
        if (c == null) return null;
        return new CompositionSliceResponse(c.label(), toMoneyView(c.amount()), c.pct());
    }

    public static CalendarEntryResponse toCalendarEntryResponse(CalendarEntry e) {
        if (e == null) return null;
        return new CalendarEntryResponse(e.date(), e.label(), toMoneyView(e.amount()), e.kind());
    }

    public static TransactionsSummaryResponse toTransactionsSummaryResponse(TransactionsSummary s) {
        if (s == null) return null;
        return new TransactionsSummaryResponse(toMoneyView(s.income()), toMoneyView(s.expense()), toMoneyView(s.net()), s.count());
    }

    public static TransactionsPageResponse toTransactionsPageResponse(TransactionsPage p) {
        if (p == null) return null;
        List<TransactionRowResponse> rows = p.rows() == null ? List.of() : p.rows().stream().map(BffMapper::toTransactionRowResponse).toList();
        return new TransactionsPageResponse(rows, p.page(), p.size(), p.totalElements(), p.totalPages());
    }

    public static FilterOptionsResponse toFilterOptionsResponse(FilterOptions f) {
        if (f == null) return null;
        List<AccountOptionResponse> accounts = f.accounts() == null ? List.of() : f.accounts().stream().map(a -> new AccountOptionResponse(a.cbu(), a.alias())).toList();
        List<CategoryOptionResponse> categories = f.categories() == null ? List.of() : f.categories().stream().map(c -> new CategoryOptionResponse(c.id(), c.name())).toList();
        return new FilterOptionsResponse(accounts, categories, f.methods() != null ? f.methods() : List.of());
    }

    public static TransactionDetailResponse toTransactionDetailResponse(TransactionDetailData d) {
        if (d == null) return null;
        TransactionOriginResponse origin = d.origin() == null ? null : new TransactionOriginResponse(d.origin().runId(), d.origin().fileName(), d.origin().importedAt(), d.origin().reconciled());
        return new TransactionDetailResponse(toTransactionRowResponse(d.transaction()), origin);
    }

    public static CategoriesKpisResponse toCategoriesKpisResponse(CategoriesKpis k) {
        if (k == null) return null;
        return new CategoriesKpisResponse(toMoneyView(k.spent()), toMoneyView(k.available()), k.overBudgetCount(), k.pacePct());
    }

    public static BudgetRowResponse toBudgetRowResponse(BudgetRow b) {
        if (b == null) return null;
        return new BudgetRowResponse(b.categoryId(), b.name(), b.cap(), toMoneyView(b.spent()), b.pct(), b.alertThresholdPct(), b.over());
    }

    public static CategoryTrendResponse toCategoryTrendResponse(CategoryTrend t) {
        if (t == null) return null;
        List<CategoryTrendPointResponse> points = t.points() == null ? List.of() : t.points().stream().map(p -> new CategoryTrendPointResponse(p.month(), toMoneyView(p.amount()))).toList();
        return new CategoryTrendResponse(t.categoryId(), points);
    }

    public static RuleRowResponse toRuleRowResponse(RuleRow r) {
        if (r == null) return null;
        return new RuleRowResponse(r.id(), r.matcher(), r.categoryId(), r.categoryName(), r.priority());
    }

    public static MarketQuoteResponse toMarketQuoteResponse(MarketQuote q) {
        if (q == null) return null;
        return new MarketQuoteResponse(q.code(), q.label(), q.value(), q.variation(), q.unit() != null ? q.unit().name() : "PERCENT", q.observedAt());
    }

    public static InvestmentsKpisResponse toInvestmentsKpisResponse(InvestmentsKpis k) {
        if (k == null) return null;
        return new InvestmentsKpisResponse(toMoneyView(k.marketValue()), toMoneyView(k.cost()), toMoneyView(k.pnl()), k.pnlPct());
    }

    public static EvolutionPointResponse toEvolutionPointResponse(EvolutionPoint e) {
        if (e == null) return null;
        return new EvolutionPointResponse(e.date(), toMoneyView(e.marketValue()), toMoneyView(e.cost()));
    }

    public static PositionRowResponse toPositionRowResponse(PositionRow p) {
        if (p == null) return null;
        return new PositionRowResponse(p.holdingId(), p.ticker(), p.name(), p.quantity(), toMoneyView(p.avgCost()), toMoneyView(p.price()), toMoneyView(p.marketValue()), toMoneyView(p.pnl()), p.pnlPct(), p.bankNumber());
    }

    public static OperationRowResponse toOperationRowResponse(OperationRow o) {
        if (o == null) return null;
        return new OperationRowResponse(o.holdingId(), o.ticker(), o.kind() != null ? o.kind().name() : "BUY", o.date(), o.quantity(), toMoneyView(o.amount()));
    }

    public static AlertRowResponse toAlertRowResponse(AlertRow a) {
        if (a == null) return null;
        return new AlertRowResponse(a.id(), a.title(), a.message(), a.createdAt(), a.read());
    }

    public static ActiveRunResponse toActiveRunResponse(ActiveRun a) {
        if (a == null) return null;
        return new ActiveRunResponse(a.runId(), a.status(), a.fileName(), a.startedAt(), a.processed(), a.total());
    }

    public static ImportRunRowResponse toImportRunRowResponse(ImportRunRow r) {
        if (r == null) return null;
        return new ImportRunRowResponse(r.runId(), r.fileName(), r.importedAt(), r.accountCbu(), r.inserted(), r.duplicates(), r.failed(), r.status());
    }

    public static ReconciliationRowResponse toReconciliationRowResponse(ReconciliationRow r) {
        if (r == null) return null;
        return new ReconciliationRowResponse(r.runId(), toMoneyView(r.expectedBalance()), toMoneyView(r.computedBalance()), r.matches());
    }

    public static UserProfileResponse toUserProfileResponse(UserProfile p) {
        if (p == null) return null;
        return new UserProfileResponse(p.name(), p.email(), p.createdAt());
    }

    public static UserPreferencesResponse toUserPreferencesResponse(UserPreferences p) {
        if (p == null) return null;
        return new UserPreferencesResponse(p.primaryCurrency(), p.secondaryCurrency(), p.numberFormat(), p.decimals(), p.useGainLossColors());
    }

    public static FeeRowResponse toFeeRowResponse(FeeRow f) {
        if (f == null) return null;
        return new FeeRowResponse(f.scope(), f.label(), toMoneyView(f.amount()), f.pct(), f.ivaTreatment());
    }

    public static FeesSummaryResponse toFeesSummaryResponse(FeesSummary f) {
        if (f == null) return null;
        List<FeeRowResponse> accounts = f.accounts() == null ? List.of() : f.accounts().stream().map(BffMapper::toFeeRowResponse).toList();
        List<FeeRowResponse> cards = f.cards() == null ? List.of() : f.cards().stream().map(BffMapper::toFeeRowResponse).toList();
        List<FeeRowResponse> brokers = f.brokers() == null ? List.of() : f.brokers().stream().map(BffMapper::toFeeRowResponse).toList();
        return new FeesSummaryResponse(accounts, cards, brokers, f.debitCreditTaxRate());
    }

    public static NotificationPreferenceResponse toNotificationPreferenceResponse(NotificationPreference n) {
        if (n == null) return null;
        return new NotificationPreferenceResponse(n.category(), n.channels());
    }

    public static SessionRowResponse toSessionRowResponse(SessionRow s) {
        if (s == null) return null;
        return new SessionRowResponse(s.id(), s.device(), s.ip(), s.lastSeenAt(), s.current());
    }

    public static SearchHitResponse toSearchHitResponse(SearchHit h) {
        if (h == null) return null;
        return new SearchHitResponse(h.id(), h.label(), h.sublabel(), h.href());
    }
}
