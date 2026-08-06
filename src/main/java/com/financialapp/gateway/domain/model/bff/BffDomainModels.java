package com.financialapp.gateway.domain.model.bff;

import com.financialapp.gateway.domain.model.composition.Section;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class BffDomainModels {

    public enum TransactionDirection { IN, OUT }
    public enum ImportStatus { OK, STALE, NEVER }
    public enum MarketQuoteUnit { PERCENT, POINTS, CURRENCY }
    public enum OperationKind { BUY, SELL }

    public record TransactionRow(
            Long id,
            LocalDate date,
            String description,
            String accountCbu,
            String accountAlias,
            Long categoryId,
            String categoryName,
            String method,
            String note,
            MoneyFigure amount,
            TransactionDirection direction
    ) {}

    public record OverviewKpis(MoneyFigure cash, MoneyFigure income, MoneyFigure expense, MoneyFigure committed) {
        public static OverviewKpis empty() {
            return new OverviewKpis(null, null, null, null);
        }
    }

    public record NetWorthPoint(LocalDate date, MoneyFigure value) {}
    public record NetWorthDelta(MoneyFigure amount, BigDecimal pct) {}
    public record NetWorth(List<NetWorthPoint> series, NetWorthDelta delta, boolean allTimeHigh) {
        public static NetWorth empty() {
            return new NetWorth(List.of(), new NetWorthDelta(null, BigDecimal.ZERO), false);
        }
    }

    public record Breakdown(MoneyFigure investments, MoneyFigure cash, MoneyFigure debt, MoneyFigure savings) {
        public static Breakdown empty() {
            return new Breakdown(null, null, null, null);
        }
    }

    public record FlowPoint(String month, MoneyFigure income, MoneyFigure expense) {}
    public record CommittedPoint(String month, MoneyFigure amount) {}
    public record UpcomingPayment(String id, String label, LocalDate dueDate, MoneyFigure amount, String kind) {}
    public record CategorySpend(Long categoryId, String name, MoneyFigure amount, BigDecimal pct) {}

    public record BanksKpis(MoneyFigure totalCash, MoneyFigure cardDebt, MoneyFigure loanBalance, Integer accountCount) {
        public static BanksKpis empty() {
            return new BanksKpis(null, null, null, 0);
        }
    }

    public record AccountRow(String cbu, String alias, String bankName, String type, MoneyFigure balance) {}
    public record CardRow(String cardNumber, String brand, String alias, BigDecimal limit, MoneyFigure used, BigDecimal usedPct, LocalDate closingDate, LocalDate dueDate) {}
    public record LoanRow(Long id, String label, BigDecimal principal, MoneyFigure outstanding, LocalDate nextInstallmentDate, Integer installmentsPaid, Integer installmentsTotal) {}
    public record ImportHealthRow(String cbu, String alias, Instant lastImportAt, Long daysSince, ImportStatus status) {}
    public record CompositionSlice(String label, MoneyFigure amount, BigDecimal pct) {}
    public record CalendarEntry(LocalDate date, String label, MoneyFigure amount, String kind) {}

    public record TransactionsSummary(MoneyFigure income, MoneyFigure expense, MoneyFigure net, Long count) {
        public static TransactionsSummary empty() {
            return new TransactionsSummary(null, null, null, 0L);
        }
    }

    public record TransactionsPage(List<TransactionRow> rows, Integer page, Integer size, Long totalElements, Integer totalPages) {
        public static TransactionsPage empty() {
            return new TransactionsPage(List.of(), 0, 10, 0L, 0);
        }
    }

    public record AccountOption(String cbu, String alias) {}
    public record CategoryOption(Long id, String name) {}
    public record FilterOptions(List<AccountOption> accounts, List<CategoryOption> categories, List<String> methods) {
        public static FilterOptions empty() {
            return new FilterOptions(List.of(), List.of(), List.of());
        }
    }

    public record UncategorisedSummary(Long count) {}

    public record TransactionOrigin(Long runId, String fileName, Instant importedAt, Boolean reconciled) {}
    public record TransactionDetailData(TransactionRow transaction, TransactionOrigin origin) {
        public static TransactionDetailData empty() {
            return new TransactionDetailData(null, null);
        }
    }

    public record CategoriesKpis(MoneyFigure spent, MoneyFigure available, Integer overBudgetCount, BigDecimal pacePct) {
        public static CategoriesKpis empty() {
            return new CategoriesKpis(null, null, 0, BigDecimal.ZERO);
        }
    }

    public record BudgetRow(Long categoryId, String name, BigDecimal cap, MoneyFigure spent, BigDecimal pct, BigDecimal alertThresholdPct, Boolean over) {}
    public record CategoryTrendPoint(String month, MoneyFigure amount) {}
    public record CategoryTrend(Long categoryId, List<CategoryTrendPoint> points) {
        public static CategoryTrend empty() {
            return new CategoryTrend(null, List.of());
        }
    }

    public record RuleRow(Long id, String matcher, Long categoryId, String categoryName, Integer priority) {}

    public record MarketQuote(String code, String label, BigDecimal value, BigDecimal variation, MarketQuoteUnit unit, Instant observedAt) {}
    public record InvestmentsKpis(MoneyFigure marketValue, MoneyFigure cost, MoneyFigure pnl, BigDecimal pnlPct) {
        public static InvestmentsKpis empty() {
            return new InvestmentsKpis(null, null, null, BigDecimal.ZERO);
        }
    }

    public record EvolutionPoint(LocalDate date, MoneyFigure marketValue, MoneyFigure cost) {}
    public record PositionRow(Long holdingId, String ticker, String name, BigDecimal quantity, MoneyFigure avgCost, MoneyFigure price, MoneyFigure marketValue, MoneyFigure pnl, BigDecimal pnlPct, String bankNumber) {}
    public record OperationRow(Long holdingId, String ticker, OperationKind kind, LocalDate date, BigDecimal quantity, MoneyFigure amount) {}
    public record AlertRow(Long id, String title, String message, Instant createdAt, Boolean read) {}

    public record ActiveRun(Long runId, String status, String fileName, Instant startedAt, Integer processed, Integer total) {}
    public record ImportRunRow(Long runId, String fileName, Instant importedAt, String accountCbu, Integer inserted, Integer duplicates, Integer failed, String status) {}
    public record ReconciliationRow(Long runId, MoneyFigure expectedBalance, MoneyFigure computedBalance, Boolean matches) {}

    public record UserProfile(String name, String email, Instant createdAt) {
        public static UserProfile empty() {
            return new UserProfile("", "", Instant.EPOCH);
        }
    }

    public record UserPreferences(String primaryCurrency, String secondaryCurrency, String numberFormat, Integer decimals, Boolean useGainLossColors) {
        public static UserPreferences empty() {
            return new UserPreferences("ARS", "USD_MEP", "1.234,56", 2, true);
        }
    }

    public record FeeRow(String scope, String label, MoneyFigure amount, BigDecimal pct, String ivaTreatment) {}
    public record FeesSummary(List<FeeRow> accounts, List<FeeRow> cards, List<FeeRow> brokers, BigDecimal debitCreditTaxRate) {
        public static FeesSummary empty() {
            return new FeesSummary(List.of(), List.of(), List.of(), BigDecimal.ZERO);
        }
    }

    public record NotificationPreference(String category, List<String> channels) {}
    public record SessionRow(String id, String device, String ip, Instant lastSeenAt, Boolean current) {}
    public record SearchHit(String id, String label, String sublabel, String href) {}
}
