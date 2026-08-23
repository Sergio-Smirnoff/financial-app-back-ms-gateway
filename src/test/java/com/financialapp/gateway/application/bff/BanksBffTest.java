package com.financialapp.gateway.application.bff;

import com.financialapp.gateway.application.bff.impl.GetBanksBffUseCaseImpl;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.gateway.UploadGateway;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.ImportHealthRow;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.ImportStatus;
import com.financialapp.gateway.domain.model.bff.BanksBffData;
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
class BanksBffTest {

    @Mock private BanksGateway banks;
    @Mock private InvestmentsGateway investments;
    @Mock private UploadGateway upload;

    private GetBanksBffUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetBanksBffUseCaseImpl(banks, investments, upload, PageTimeoutBudget.fromMillis(3000));
    }

    @Test
    void execute_returnsOkSectionsWhenAllDownstreamsSucceed() {
        when(banks.fetchAccounts(any())).thenReturn(CompletableFuture.completedFuture(List.of(Map.of("cbu", "01700001"))));
        when(banks.fetchCards(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchLoans(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(upload.fetchHistory(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchUpcomingPayments(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));

        BanksBffData data = useCase.execute(new UserId(1L), CurrencyView.ARS, "none").join();

        assertThat(data.accounts().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.importHealth().status()).isEqualTo(SectionStatus.OK);
    }

    @Test
    void execute_degradesSectionToUnavailableWhenUploadFails() {
        when(banks.fetchAccounts(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchCards(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchLoans(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(upload.fetchHistory(any())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("Upload down")));
        when(banks.fetchUpcomingPayments(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));

        BanksBffData data = useCase.execute(new UserId(1L), CurrencyView.ARS, "none").join();

        assertThat(data.importHealth().status()).isEqualTo(SectionStatus.UNAVAILABLE);
        assertThat(data.accounts().status()).isEqualTo(SectionStatus.OK);
    }

    @Test
    void importHealthMarksAccountsNeverImported() {
        when(banks.fetchAccounts(any())).thenReturn(CompletableFuture.completedFuture(List.of(Map.of("cbu", "01700001", "alias", "Sueldo"))));
        when(banks.fetchCards(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchLoans(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(upload.fetchHistory(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchUpcomingPayments(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));

        BanksBffData data = useCase.execute(new UserId(1L), CurrencyView.ARS, "none").join();

        ImportHealthRow row = data.importHealth().data().getFirst();
        assertThat(row.status()).isEqualTo(ImportStatus.NEVER);
        assertThat(row.daysSince()).isNull();
    }

    @Test
    void loans_section_reads_real_ms_banks_keys_and_computes_outstanding() {
        when(banks.fetchAccounts(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchCards(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchLoans(any())).thenReturn(CompletableFuture.completedFuture(List.of(Map.of(
                "id", 1, "name", "Auto", "principal", "18000.00", "currency", "ARS",
                "totalInstallments", 12, "remainingInstallments", 10, "active", true))));
        when(banks.fetchLoanInstallments(any(), eq(1L))).thenReturn(CompletableFuture.completedFuture(List.of(
                Map.of("id", 11, "installmentNumber", 1, "amount", "1800.00", "dueDate", "2026-08-10", "paid", true),
                Map.of("id", 12, "installmentNumber", 2, "amount", "1800.00", "dueDate", "2026-09-10", "paid", false))));
        when(upload.fetchHistory(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchUpcomingPayments(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));

        BanksBffData data = useCase.execute(new UserId(1L), CurrencyView.ARS, "none").join();

        var row = data.loans().data().getFirst();
        assertThat(row.label()).isEqualTo("Auto");
        assertThat(row.principal()).isEqualByComparingTo(new BigDecimal("18000.00"));
        assertThat(row.outstanding().amount()).isEqualByComparingTo(new BigDecimal("1800.00"));
        assertThat(row.installmentsPaid()).isEqualTo(2);
        assertThat(row.installmentsTotal()).isEqualTo(12);
        assertThat(row.nextInstallmentDate()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(data.kpis().data().loanBalance().amount()).isEqualByComparingTo(new BigDecimal("1800.00"));
    }
}
