package com.financialapp.gateway.web;

import com.financialapp.gateway.domain.model.bff.BffDomainModels.*;
import com.financialapp.gateway.domain.model.bff.OverviewBffData;
import com.financialapp.gateway.domain.model.bff.TransactionQuery;
import com.financialapp.gateway.domain.model.bff.TransactionsBffData;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.usecase.bff.*;
import com.financialapp.gateway.web.controller.bff.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Clock;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BffRouteTest {

    @Mock private GetOverviewBffUseCase getOverviewBffUseCase;
    @Mock private GetBanksBffUseCase getBanksBffUseCase;
    @Mock private GetTransactionsBffUseCase getTransactionsBffUseCase;
    @Mock private GetTransactionDetailBffUseCase getTransactionDetailBffUseCase;
    @Mock private GetCategoriesBffUseCase getCategoriesBffUseCase;
    @Mock private GetInvestmentsBffUseCase getInvestmentsBffUseCase;
    @Mock private GetImportsBffUseCase getImportsBffUseCase;
    @Mock private GetSettingsBffUseCase getSettingsBffUseCase;
    @Mock private GetSearchBffUseCase getSearchBffUseCase;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(
                new OverviewBffController(getOverviewBffUseCase),
                new BanksBffController(getBanksBffUseCase),
                new TransactionsBffController(getTransactionsBffUseCase, getTransactionDetailBffUseCase),
                new CategoriesBffController(getCategoriesBffUseCase),
                new InvestmentsBffController(getInvestmentsBffUseCase),
                new ImportsBffController(getImportsBffUseCase),
                new SettingsBffController(getSettingsBffUseCase),
                new SearchBffController(getSearchBffUseCase)
        ).build();
    }

    @Test
    void bffPathsAreRoutedLocallyAndNotProxied() {
        ObservedAt now = ObservedAt.now(Clock.systemUTC());
        when(getOverviewBffUseCase.execute(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(new OverviewBffData(
                        Section.unavailable(null, now), Section.unavailable(null, now), Section.unavailable(null, now),
                        Section.unavailable(null, now), Section.unavailable(null, now), Section.unavailable(null, now),
                        Section.unavailable(null, now), Section.unavailable(null, now))));

        webTestClient.get().uri("/api/v1/bff/overview")
                .header("X-User-Id", "1")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void transactionsRouteAcceptsTheFilterQueryParameters() {
        ObservedAt stamp = ObservedAt.now(Clock.systemUTC());
        when(getTransactionsBffUseCase.execute(any(), any(TransactionQuery.class), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(new TransactionsBffData(
                        Section.ok(TransactionsSummary.empty(), stamp),
                        Section.ok(TransactionsPage.empty(), stamp),
                        Section.ok(FilterOptions.empty(), stamp),
                        Section.ok(new UncategorisedSummary(0L), stamp))));

        webTestClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/bff/transactions")
                        .queryParam("categories", "none")
                        .queryParam("accounts", "0001112223334445556667")
                        .queryParam("method", "CREDIT_CARD")
                        .queryParam("q", "super")
                        .queryParam("page", "1")
                        .build())
                .header("X-User-Id", "1")
                .exchange()
                .expectStatus().isOk();

        ArgumentCaptor<TransactionQuery> captured = ArgumentCaptor.forClass(TransactionQuery.class);
        verify(getTransactionsBffUseCase).execute(any(), captured.capture(), any(), any());
        assertThat(captured.getValue().onlyUncategorised()).isTrue();
        assertThat(captured.getValue().accounts()).containsExactly("0001112223334445556667");
        assertThat(captured.getValue().method()).isEqualTo("CREDIT_CARD");
        assertThat(captured.getValue().query()).isEqualTo("super");
        assertThat(captured.getValue().page()).isEqualTo(1);
    }
}
