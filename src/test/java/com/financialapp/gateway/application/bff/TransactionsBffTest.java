package com.financialapp.gateway.application.bff;

import com.financialapp.gateway.application.bff.impl.GetTransactionDetailBffUseCaseImpl;
import com.financialapp.gateway.application.bff.impl.GetTransactionsBffUseCaseImpl;
import com.financialapp.gateway.contracts.DownstreamFixtures;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.exception.ResourceNotFoundException;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.gateway.FinancesGateway;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.gateway.UploadGateway;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.AccountOption;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.CategoryOption;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.TransactionDirection;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.TransactionOrigin;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.TransactionRow;
import com.financialapp.gateway.domain.model.bff.TransactionDetailBffData;
import com.financialapp.gateway.domain.model.bff.TransactionQuery;
import com.financialapp.gateway.domain.model.bff.TransactionsBffData;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.SectionStatus;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionsBffTest {

    @Mock private FinancesGateway finances;
    @Mock private BanksGateway banks;
    @Mock private InvestmentsGateway investments;
    @Mock private UploadGateway upload;

    private GetTransactionsBffUseCaseImpl listUseCase() {
        return new GetTransactionsBffUseCaseImpl(finances, banks, investments, PageTimeoutBudget.fromMillis(3000));
    }

    private void stubList(Map<String, Object> page) {
        when(finances.fetchSummary(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(finances.fetchTransactions(any(), any(TransactionQuery.class))).thenReturn(CompletableFuture.completedFuture(page));
        when(finances.fetchCategories(any())).thenReturn(CompletableFuture.completedFuture(
                DownstreamFixtures.list("finances/categories.json")));
        when(banks.fetchAccounts(any())).thenReturn(CompletableFuture.completedFuture(
                DownstreamFixtures.list("banks/accounts.json")));
        when(finances.fetchUncategorisedCount(any())).thenReturn(CompletableFuture.completedFuture(Map.of("count", 0)));
    }

    private TransactionsBffData list(int page) {
        return listUseCase().execute(
                new UserId(1L),
                new TransactionQuery(page, 20, List.of(), List.of(), null, null, null, null),
                CurrencyView.ARS, "none").join();
    }

    @Test
    void execute_returnsOkSectionsForTransactionsList() {
        stubList(Map.of("content", List.of(), "totalElements", 0));

        TransactionsBffData data = list(0);

        assertThat(data.summary().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.page().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.filterOptions().status()).isEqualTo(SectionStatus.OK);
    }

    @Test
    void pageMetadataIsDerivedFromTheCursorPageEnvelope() {
        stubList(Map.of("content", List.of(), "hasNext", true, "nextCursor", "abc", "totalElements", 45));

        TransactionsBffData data = list(1);

        assertThat(data.page().data().page()).isEqualTo(1);
        assertThat(data.page().data().size()).isEqualTo(20);
        assertThat(data.page().data().totalElements()).isEqualTo(45L);
        assertThat(data.page().data().totalPages()).isEqualTo(3);
    }

    @Test
    void rowsNameTheOwnAccountItsLabelAndThePaymentMethod() {
        stubList(DownstreamFixtures.object("finances/transactions-page.json"));

        List<TransactionRow> rows = list(0).page().data().rows();

        assertThat(rows).extracting(TransactionRow::accountCbu)
                .containsExactly("0170099200000000000017", "0170099200000000000017");
        assertThat(rows).extracting(TransactionRow::accountAlias).containsExactly("demo.checking", "demo.checking");
        assertThat(rows).extracting(TransactionRow::method).containsExactly("TRANSFER", "DEBIT_CARD");
        assertThat(rows).extracting(TransactionRow::direction)
                .containsExactly(TransactionDirection.IN, TransactionDirection.OUT);
        assertThat(rows).extracting(TransactionRow::categoryName).containsExactly("Sueldo", "");
    }

    @Test
    void filterOptionsListEveryCategoryAndSubcategoryAndLabelAccounts() {
        stubList(Map.of("content", List.of(), "totalElements", 0));

        TransactionsBffData data = list(0);

        assertThat(data.filterOptions().data().categories()).extracting(CategoryOption::id, CategoryOption::name)
                .containsExactly(
                        tuple(1L, "Supermercado"),
                        tuple(11L, "Supermercado / Mayorista"),
                        tuple(2L, "Transporte"));
        assertThat(data.filterOptions().data().accounts()).extracting(AccountOption::alias)
                .containsExactly("demo.checking", "Caja de Ahorro");
    }

    @Test
    void aRowWithoutItsKindMakesThePageUnavailable() {
        stubList(Map.of("content", List.of(Map.of("id", 1, "amount", "5.00", "currency", "ARS")), "totalElements", 1));

        assertThat(list(0).page().status()).isEqualTo(SectionStatus.UNAVAILABLE);
    }

    private GetTransactionDetailBffUseCaseImpl detailUseCase() {
        return new GetTransactionDetailBffUseCaseImpl(finances, upload, banks, PageTimeoutBudget.fromMillis(3000));
    }

    @Test
    void detailReadsTheRealTransactionAndImportRunKeys() {
        when(finances.fetchTransactionById(any(), eq(102L))).thenReturn(CompletableFuture.completedFuture(
                DownstreamFixtures.object("finances/transaction.json")));
        when(upload.fetchRunByTransaction(any(), eq(102L))).thenReturn(CompletableFuture.completedFuture(
                DownstreamFixtures.object("upload/import-run.json")));
        when(banks.fetchAccounts(any())).thenReturn(CompletableFuture.completedFuture(
                DownstreamFixtures.list("banks/accounts.json")));

        TransactionDetailBffData data = detailUseCase().execute(new UserId(1L), 102L).join();

        assertThat(data.detail().status()).isEqualTo(SectionStatus.OK);
        TransactionRow transaction = data.detail().data().transaction();
        assertThat(transaction.id()).isEqualTo(102L);
        assertThat(transaction.accountCbu()).isEqualTo("0170099200000000000017");
        assertThat(transaction.accountAlias()).isEqualTo("demo.checking");
        assertThat(transaction.method()).isEqualTo("CREDIT_CARD");
        assertThat(transaction.direction()).isEqualTo(TransactionDirection.OUT);
        TransactionOrigin origin = data.detail().data().origin();
        assertThat(origin.runId()).isEqualTo(50L);
        assertThat(origin.importedAt()).isEqualTo(Instant.parse("2026-09-12T15:04:05Z"));
        assertThat(origin.reconciled()).isTrue();
        assertThat(origin.fileName()).isNull();
    }

    @Test
    void aTransactionWithoutAnImportRunHasNoOrigin() {
        when(finances.fetchTransactionById(any(), eq(102L))).thenReturn(CompletableFuture.completedFuture(
                DownstreamFixtures.object("finances/transaction.json")));
        when(upload.fetchRunByTransaction(any(), eq(102L))).thenReturn(CompletableFuture.completedFuture(Map.of()));
        when(banks.fetchAccounts(any())).thenReturn(CompletableFuture.completedFuture(List.of()));

        TransactionDetailBffData data = detailUseCase().execute(new UserId(1L), 102L).join();

        assertThat(data.detail().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.detail().data().origin()).isNull();
    }

    @Test
    void aMissingTransactionFailsWithNotFoundInsteadOfAnEmptyDetail() {
        when(finances.fetchTransactionById(any(), eq(999L)))
                .thenReturn(CompletableFuture.failedFuture(new ResourceNotFoundException("Transaction", 999L)));
        when(upload.fetchRunByTransaction(any(), eq(999L))).thenReturn(CompletableFuture.completedFuture(Map.of()));
        when(banks.fetchAccounts(any())).thenReturn(CompletableFuture.completedFuture(List.of()));

        assertThatThrownBy(() -> detailUseCase().execute(new UserId(1L), 999L).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aFinancesOutageDegradesTheDetailSection() {
        when(finances.fetchTransactionById(any(), eq(5L)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("finances down")));
        when(upload.fetchRunByTransaction(any(), eq(5L))).thenReturn(CompletableFuture.completedFuture(Map.of()));
        when(banks.fetchAccounts(any())).thenReturn(CompletableFuture.completedFuture(List.of()));

        TransactionDetailBffData data = detailUseCase().execute(new UserId(1L), 5L).join();

        assertThat(data.detail().status()).isEqualTo(SectionStatus.UNAVAILABLE);
    }
}
