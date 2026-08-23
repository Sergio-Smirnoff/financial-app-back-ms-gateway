package com.financialapp.gateway.application.bff;

import com.financialapp.gateway.application.bff.impl.GetLoansBffUseCaseImpl;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.model.bff.LoansBffData;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.SectionStatus;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoansBffTest {

    @Mock private BanksGateway banks;
    @Mock private InvestmentsGateway investments;

    private GetLoansBffUseCaseImpl useCase;

    private static final Map<String, Object> LOAN = Map.of(
            "id", 1, "bankNumber", "017", "name", "Auto", "principal", "18000.00",
            "currency", "ARS", "interestRate", "45.00", "totalInstallments", 12,
            "remainingInstallments", 10, "active", true);

    private static final List<Map<String, Object>> SCHEDULE = List.of(
            Map.of("id", 11, "installmentNumber", 1, "amount", "1800.00", "dueDate", "2026-08-10", "paid", true),
            Map.of("id", 12, "installmentNumber", 2, "amount", "1800.00", "dueDate", "2026-09-10", "paid", false),
            Map.of("id", 13, "installmentNumber", 3, "amount", "1800.00", "dueDate", "2026-10-10", "paid", false));

    @BeforeEach
    void setUp() {
        useCase = new GetLoansBffUseCaseImpl(banks, investments, PageTimeoutBudget.fromMillis(3000));
    }

    @Test
    void composes_kpis_and_rows_from_loans_plus_schedules() {
        when(banks.fetchLoans(any())).thenReturn(CompletableFuture.completedFuture(List.of(LOAN)));
        when(banks.fetchLoanInstallments(any(), eq(1L))).thenReturn(CompletableFuture.completedFuture(SCHEDULE));
        when(banks.fetchAccounts(any())).thenReturn(CompletableFuture.completedFuture(List.of(Map.of("cbu", "0170000001", "alias", "Sueldo"))));

        LoansBffData data = useCase.execute(new UserId(1L), CurrencyView.ARS, "none").join();

        assertThat(data.kpis().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.kpis().data().activeLoans()).isEqualTo(1);
        assertThat(data.kpis().data().totalOutstanding().amount()).isEqualByComparingTo(new BigDecimal("3600.00"));
        assertThat(data.kpis().data().monthlyPayment().amount()).isEqualByComparingTo(new BigDecimal("1800.00"));

        var row = data.loans().data().getFirst();
        assertThat(row.label()).isEqualTo("Auto");
        assertThat(row.installmentsPaid()).isEqualTo(2);
        assertThat(row.installmentsTotal()).isEqualTo(12);
        assertThat(row.outstanding().amount()).isEqualByComparingTo(new BigDecimal("3600.00"));
        assertThat(row.nextInstallmentDate()).isEqualTo(LocalDate.of(2026, 9, 10));

        assertThat(data.payFromAccounts().data().getFirst().cbu()).isEqualTo("0170000001");
    }

    @Test
    void loans_section_degrades_to_unavailable_when_upstream_fails() {
        when(banks.fetchLoans(any())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("banks down")));
        when(banks.fetchAccounts(any())).thenReturn(CompletableFuture.completedFuture(List.of()));

        LoansBffData data = useCase.execute(new UserId(1L), CurrencyView.ARS, "none").join();

        assertThat(data.loans().status()).isEqualTo(SectionStatus.UNAVAILABLE);
        assertThat(data.kpis().status()).isEqualTo(SectionStatus.UNAVAILABLE);
        assertThat(data.payFromAccounts().status()).isEqualTo(SectionStatus.OK);
    }
}
