package com.financialapp.gateway.application.bff;

import com.financialapp.gateway.application.bff.impl.GetOverviewBffUseCaseImpl;
import com.financialapp.gateway.contracts.DownstreamFixtures;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.gateway.FinancesGateway;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.gateway.NotificationsGateway;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.CategorySpend;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.TransactionDirection;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.TransactionRow;
import com.financialapp.gateway.domain.model.bff.CurrencySummary;
import com.financialapp.gateway.domain.model.bff.MoneyFigure;
import com.financialapp.gateway.domain.model.bff.OverviewBffData;
import com.financialapp.gateway.domain.model.bff.TransactionQuery;
import com.financialapp.gateway.domain.model.bff.UpcomingPaymentView;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.SectionStatus;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.model.currency.FxRate;
import com.financialapp.gateway.domain.model.currency.FxRateMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OverviewBffTest {

    @Mock private FinancesGateway finances;
    @Mock private BanksGateway banks;
    @Mock private InvestmentsGateway investments;
    @Mock private NotificationsGateway notifications;

    private GetOverviewBffUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetOverviewBffUseCaseImpl(
                finances, banks, investments, notifications,
                PageTimeoutBudget.fromMillis(3000));
        lenient().when(finances.fetchSummary(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        lenient().when(investments.fetchPortfolioSummary(any())).thenReturn(CompletableFuture.completedFuture(
                DownstreamFixtures.object("investments/portfolio-summary.json")));
        lenient().when(finances.fetchMonthlyFlow(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        lenient().when(banks.fetchUpcomingPayments(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        lenient().when(banks.fetchAccounts(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        lenient().when(banks.fetchCards(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        lenient().when(banks.fetchLoans(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        lenient().when(finances.fetchSpendByCategory(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        lenient().when(finances.fetchTransactions(any(), any(TransactionQuery.class)))
                .thenReturn(CompletableFuture.completedFuture(Map.of("content", List.of())));
    }

    private OverviewBffData inArs() {
        return useCase.execute(new UserId(1L), CurrencyView.ARS, "none").join();
    }

    @Test
    void execute_returnsOkSectionsWhenAllDownstreamsSucceed() {
        OverviewBffData data = inArs();

        assertThat(data.kpis().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.netWorth().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.breakdown().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.spendByCategory().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.latestMovements().status()).isEqualTo(SectionStatus.OK);
    }

    @Test
    void execute_degradesSectionToUnavailableWhenDownstreamFails() {
        when(finances.fetchSummary(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Finances down")));

        OverviewBffData data = inArs();

        assertThat(data.kpis().status()).isEqualTo(SectionStatus.UNAVAILABLE);
        assertThat(data.netWorth().status()).isEqualTo(SectionStatus.OK);
    }

    @Test
    void kpisCarrySecondaryWhenRateExists() {
        when(investments.fetchFxRate(eq(CurrencyView.USD_MEP), any()))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(new FxRate(
                        LocalDate.now(), FxRateMode.MEP, new BigDecimal("1180"), new BigDecimal("1190")))));
        when(finances.fetchSummary(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of(
                new CurrencySummary("ARS", "11900.00", "0.00", "11900.00"))));

        OverviewBffData data = useCase.execute(new UserId(1L), CurrencyView.USD_MEP, "ARS").join();

        MoneyFigure cash = data.kpis().data().cash();
        assertThat(cash.currency().getCurrencyCode()).isEqualTo("USD");
        assertThat(cash.secondary().currency().getCurrencyCode()).isEqualTo("ARS");
    }

    @Test
    void kpisOmitSecondaryWhenNoRateExists() {
        when(investments.fetchFxRate(any(), any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        OverviewBffData data = useCase.execute(new UserId(1L), CurrencyView.USD_MEP, "ARS").join();

        assertThat(data.kpis().data().cash().secondary()).isNull();
    }

    @Test
    void kpisCommitAndBreakdownComposeFromBanksAndPortfolio() {
        when(banks.fetchUpcomingPayments(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of(
                new UpcomingPaymentView(1L, "LOAN", "Cuota", "25000.00", "ARS", LocalDate.now().plusDays(10), 1, 12, false))));
        when(banks.fetchAccounts(any())).thenReturn(CompletableFuture.completedFuture(List.of(
                Map.of("type", "SAVINGS", "balance", "100000.00"),
                Map.of("type", "CHECKING", "balance", "40000.00"))));
        when(banks.fetchCards(any())).thenReturn(CompletableFuture.completedFuture(List.of(Map.of("usedAmount", "15000.00"))));
        when(banks.fetchLoans(any())).thenReturn(CompletableFuture.completedFuture(List.of(Map.of("id", 1, "name", "Auto"))));
        when(banks.fetchLoanInstallments(any(), eq(1L))).thenReturn(CompletableFuture.completedFuture(List.of(
                Map.of("id", 11, "installmentNumber", 1, "amount", "5000.00", "dueDate", "2026-09-10", "paid", false),
                Map.of("id", 10, "installmentNumber", 0, "amount", "7000.00", "dueDate", "2026-08-10", "paid", true))));

        OverviewBffData data = inArs();

        assertThat(data.kpis().data().committed().amount()).isEqualByComparingTo("25000.00");
        assertThat(data.breakdown().data().investments().amount()).isEqualByComparingTo("10661892.60");
        assertThat(data.breakdown().data().savings().amount()).isEqualByComparingTo("100000.00");
        assertThat(data.breakdown().data().cash().amount()).isEqualByComparingTo("40000.00");
        assertThat(data.breakdown().data().debt().amount()).isEqualByComparingTo("20000.00");
    }

    @Test
    void cardDebtReportsTheUsedAmountSentByMsBanks() {
        when(banks.fetchCards(any())).thenReturn(CompletableFuture.completedFuture(List.of(Map.of(
                "cardNumber", "1111", "brand", "VISA", "displayName", "Personal",
                "creditLimit", "100000.00", "usedAmount", "1666.67", "usedPercent", "1.67"))));

        OverviewBffData data = inArs();

        assertThat(data.breakdown().data().debt().amount()).isEqualByComparingTo("1666.67");
    }

    @Test
    void netWorthIsThePortfolioMarketValueOfTheByCurrencyContract() {
        OverviewBffData data = inArs();

        assertThat(data.netWorth().data().series()).singleElement()
                .satisfies(point -> assertThat(point.value().amount()).isEqualByComparingTo("10661892.60"));
    }

    @Test
    void aPortfolioWithoutByCurrencyMakesNetWorthUnavailable() {
        when(investments.fetchPortfolioSummary(any()))
                .thenReturn(CompletableFuture.completedFuture(Map.of("totalMarketValue", 1000)));

        OverviewBffData data = inArs();

        assertThat(data.netWorth().status()).isEqualTo(SectionStatus.UNAVAILABLE);
        assertThat(data.breakdown().status()).isEqualTo(SectionStatus.UNAVAILABLE);
        assertThat(data.kpis().status()).isEqualTo(SectionStatus.OK);
    }

    @Test
    void spendByCategoryReadsTotalAndDerivesItsShare() {
        when(finances.fetchSpendByCategory(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(
                DownstreamFixtures.list("finances/category-spend.json")));

        List<CategorySpend> spend = inArs().spendByCategory().data();

        assertThat(spend).extracting(CategorySpend::name).containsExactly("Supermercado", "Transporte");
        assertThat(spend.get(0).amount().amount()).isEqualByComparingTo("327500.50");
        assertThat(spend.get(0).pct()).isEqualByComparingTo("84.53");
        assertThat(spend.get(1).amount().amount()).isEqualByComparingTo("59950.00");
        assertThat(spend.get(1).pct()).isEqualByComparingTo("15.47");
    }

    @Test
    void latestMovementsNameTheOwnAccountItsLabelAndTheMethod() {
        when(finances.fetchTransactions(any(), any(TransactionQuery.class))).thenReturn(CompletableFuture.completedFuture(
                DownstreamFixtures.object("finances/transactions-page.json")));
        when(banks.fetchAccounts(any())).thenReturn(CompletableFuture.completedFuture(
                DownstreamFixtures.list("banks/accounts.json")));

        List<TransactionRow> rows = inArs().latestMovements().data();

        TransactionRow salary = rows.get(0);
        assertThat(salary.accountCbu()).isEqualTo("0170099200000000000017");
        assertThat(salary.accountAlias()).isEqualTo("demo.checking");
        assertThat(salary.method()).isEqualTo("TRANSFER");
        assertThat(salary.direction()).isEqualTo(TransactionDirection.IN);
        assertThat(salary.note()).isEmpty();
        TransactionRow groceries = rows.get(1);
        assertThat(groceries.accountCbu()).isEqualTo("0170099200000000000017");
        assertThat(groceries.method()).isEqualTo("DEBIT_CARD");
        assertThat(groceries.direction()).isEqualTo(TransactionDirection.OUT);
        assertThat(groceries.categoryName()).isEmpty();
        assertThat(groceries.note()).isEqualTo("compra semanal");
    }
}
