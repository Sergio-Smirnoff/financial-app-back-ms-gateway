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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
}
