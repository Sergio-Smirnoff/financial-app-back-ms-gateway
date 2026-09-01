package com.financialapp.gateway.web;

import com.financialapp.gateway.domain.model.bff.OverviewBffData;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.usecase.bff.*;
import com.financialapp.gateway.web.controller.bff.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Clock;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
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
}
