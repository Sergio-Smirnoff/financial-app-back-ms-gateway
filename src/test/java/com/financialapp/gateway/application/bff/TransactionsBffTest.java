package com.financialapp.gateway.application.bff;

import com.financialapp.gateway.application.bff.impl.GetTransactionDetailBffUseCaseImpl;
import com.financialapp.gateway.application.bff.impl.GetTransactionsBffUseCaseImpl;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.FinancesGateway;
import com.financialapp.gateway.domain.gateway.UploadGateway;
import com.financialapp.gateway.domain.model.bff.TransactionDetailBffData;
import com.financialapp.gateway.domain.model.bff.TransactionsBffData;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.SectionStatus;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionsBffTest {

    @Mock private FinancesGateway finances;
    @Mock private UploadGateway upload;

    @Test
    void execute_returnsOkSectionsForTransactionsList() {
        GetTransactionsBffUseCaseImpl useCase = new GetTransactionsBffUseCaseImpl(finances, PageTimeoutBudget.fromMillis(3000));

        when(finances.fetchSummary(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(finances.fetchTransactions(any(), eq(0), eq(20), any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(Map.of()));
        when(finances.fetchCategorizationRules(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(finances.fetchUncategorisedCount(any())).thenReturn(CompletableFuture.completedFuture(Map.of("count", 0)));

        TransactionsBffData data = useCase.execute(new UserId(1L), 0, 20, null, null, null, null, CurrencyView.ARS, "none").join();

        assertThat(data.summary().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.page().status()).isEqualTo(SectionStatus.OK);
    }

    @Test
    void execute_returnsOkDetailJoinedWithUploadRun() {
        GetTransactionDetailBffUseCaseImpl detailUseCase = new GetTransactionDetailBffUseCaseImpl(finances, upload, PageTimeoutBudget.fromMillis(3000));

        when(finances.fetchTransactionById(any(), eq(100L))).thenReturn(CompletableFuture.completedFuture(Map.of("id", 100L, "amount", 500)));
        when(upload.fetchRunByTransaction(any(), eq(100L))).thenReturn(CompletableFuture.completedFuture(Map.of("runId", 50L)));

        TransactionDetailBffData data = detailUseCase.execute(new UserId(1L), 100L).join();

        assertThat(data.detail().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.detail().data()).containsEntry("id", 100L);
        assertThat(data.detail().data()).containsKey("importRun");
    }
}
