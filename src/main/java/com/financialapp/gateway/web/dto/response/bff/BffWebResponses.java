package com.financialapp.gateway.web.dto.response.bff;

import com.financialapp.gateway.web.dto.response.MoneyView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class BffWebResponses {

    public record TransactionRowResponse(
            Long id,
            LocalDate date,
            String description,
            String accountCbu,
            String accountAlias,
            Long categoryId,
            String categoryName,
            String method,
            String note,
            MoneyView amount,
            String direction
    ) {}

    public record OverviewKpisResponse(
            @Schema(requiredMode = REQUIRED) MoneyView cash,
            @Schema(requiredMode = REQUIRED) MoneyView income,
            @Schema(requiredMode = REQUIRED) MoneyView expense,
            @Schema(requiredMode = REQUIRED) MoneyView committed
    ) {}

    public record NetWorthPointResponse(LocalDate date, MoneyView value) {}
    public record NetWorthDeltaResponse(MoneyView amount, BigDecimal pct) {}
    public record NetWorthResponse(List<NetWorthPointResponse> series, NetWorthDeltaResponse delta, boolean allTimeHigh) {}

    public record BreakdownResponse(
            @Schema(requiredMode = REQUIRED) MoneyView investments,
            @Schema(requiredMode = REQUIRED) MoneyView cash,
            @Schema(requiredMode = REQUIRED) MoneyView debt,
            @Schema(requiredMode = REQUIRED) MoneyView savings
    ) {}

    public record FlowPointResponse(String month, MoneyView income, MoneyView expense) {}
    public record CommittedPointResponse(String month, MoneyView amount) {}
    public record UpcomingPaymentResponse(String id, String label, LocalDate dueDate, MoneyView amount, String kind) {}
    public record CategorySpendResponse(Long categoryId, String name, MoneyView amount, BigDecimal pct) {}

    public record BanksKpisResponse(MoneyView totalCash, MoneyView cardDebt, MoneyView loanBalance, Integer accountCount) {}

    public record AccountRowResponse(String cbu, String alias, String bankName, String type, MoneyView balance) {}
    public record CardRowResponse(String cardNumber, String brand, String alias, BigDecimal limit, MoneyView used, BigDecimal usedPct, LocalDate closingDate, LocalDate dueDate) {}
    public record LoanRowResponse(Long id, String label, BigDecimal principal, MoneyView outstanding, LocalDate nextInstallmentDate, Integer installmentsPaid, Integer installmentsTotal) {}
    public record LoansKpisResponse(MoneyView totalOutstanding, MoneyView monthlyPayment, Integer activeLoans, LocalDate nextDueDate) {}
    public record LoanDetailRowResponse(Long id, String label, String bankNumber, MoneyView principal, MoneyView outstanding, BigDecimal interestRate, Integer installmentsPaid, Integer installmentsTotal, LocalDate nextInstallmentDate, MoneyView nextInstallmentAmount, Boolean active) {}
    public record InstallmentRowResponse(Long id, Integer number, MoneyView amount, LocalDate dueDate, Boolean paid, LocalDate paidDate) {}

    public record ImportHealthRowResponse(String cbu, String alias, Instant lastImportAt, Long daysSince, String status) {}
    public record CompositionSliceResponse(String label, MoneyView amount, BigDecimal pct) {}
    public record CalendarEntryResponse(LocalDate date, String label, MoneyView amount, String kind) {}

    public record TransactionsSummaryResponse(MoneyView income, MoneyView expense, MoneyView net, Long count) {}

    public record TransactionsPageResponse(List<TransactionRowResponse> rows, Integer page, Integer size, Long totalElements, Integer totalPages) {}

    public record AccountOptionResponse(String cbu, String alias) {}
    public record CategoryOptionResponse(Long id, String name) {}
    public record FilterOptionsResponse(List<AccountOptionResponse> accounts, List<CategoryOptionResponse> categories, List<String> methods) {}

    public record UncategorisedSummaryResponse(Long count) {}

    public record TransactionOriginResponse(Long runId, String fileName, Instant importedAt, Boolean reconciled) {}
    public record TransactionDetailResponse(TransactionRowResponse transaction, TransactionOriginResponse origin) {}

    public record CategoriesKpisResponse(MoneyView spent, MoneyView available, Integer overBudgetCount, BigDecimal pacePct) {}

    public record BudgetRowResponse(Long categoryId, String name, BigDecimal cap, MoneyView spent, BigDecimal pct, BigDecimal alertThresholdPct, Boolean over) {}
    public record CategoryTrendPointResponse(String month, MoneyView amount) {}
    public record CategoryTrendResponse(Long categoryId, List<CategoryTrendPointResponse> points) {}

    public record RuleRowResponse(Long id, String matcher, Long categoryId, String categoryName, Integer priority) {}

    public record MarketQuoteResponse(String code, String label, BigDecimal value, BigDecimal variation, String unit, Instant observedAt) {}
    public record InvestmentsKpisResponse(MoneyView marketValue, MoneyView cost, MoneyView pnl, BigDecimal pnlPct) {}

    public record EvolutionPointResponse(LocalDate date, MoneyView marketValue, MoneyView cost) {}
    public record PositionRowResponse(Long holdingId, String ticker, String name, BigDecimal quantity, MoneyView avgCost, MoneyView price, MoneyView marketValue, MoneyView pnl, BigDecimal pnlPct, String bankNumber) {}
    public record OperationRowResponse(Long holdingId, String ticker, String kind, LocalDate date, BigDecimal quantity, MoneyView amount) {}
    public record AlertRowResponse(Long id, String title, String message, Instant createdAt, Boolean read) {}

    public record ActiveRunResponse(Long runId, String status, String fileName, Instant startedAt, Integer processed, Integer total) {}
    public record ImportRunRowResponse(Long runId, String fileName, Instant importedAt, String accountCbu, Integer inserted, Integer duplicates, Integer failed, String status) {}
    public record ReconciliationRowResponse(Long runId, MoneyView expectedBalance, MoneyView computedBalance, Boolean matches) {}

    public record UserProfileResponse(String name, String email, Instant createdAt) {}

    public record UserPreferencesResponse(String primaryCurrency, String secondaryCurrency, String numberFormat, Integer decimals, Boolean useGainLossColors) {}

    public record FeeRowResponse(String scope, String label, MoneyView amount, BigDecimal pct, String ivaTreatment) {}
    public record FeesSummaryResponse(List<FeeRowResponse> accounts, List<FeeRowResponse> cards, List<FeeRowResponse> brokers, BigDecimal debitCreditTaxRate) {}

    public record NotificationPreferenceResponse(String category, List<String> channels) {}
    public record SessionRowResponse(String id, String device, String ip, Instant lastSeenAt, Boolean current) {}
    public record SearchHitResponse(String id, String label, String sublabel, String href) {}
}
