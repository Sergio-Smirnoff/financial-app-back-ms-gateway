package com.financialapp.gateway.web;

import com.financialapp.gateway.domain.common.model.AccessToken;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.*;
import com.financialapp.gateway.domain.model.bff.HistoryRange;
import com.financialapp.gateway.domain.model.bff.InvestmentsBffData;
import com.financialapp.gateway.domain.model.bff.MoneyFigure;
import com.financialapp.gateway.domain.model.bff.OverviewBffData;
import com.financialapp.gateway.domain.model.bff.SettingsBffData;
import com.financialapp.gateway.domain.model.bff.TransactionQuery;
import com.financialapp.gateway.domain.model.bff.TransactionsBffData;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.usecase.bff.*;
import com.financialapp.gateway.web.controller.bff.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
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

    private static SettingsBffData emptySettings() {
        ObservedAt stamp = ObservedAt.now(Clock.systemUTC());
        return new SettingsBffData(
                Section.ok(UserProfile.empty(), stamp),
                Section.ok(UserPreferences.empty(), stamp),
                Section.ok(FeesSummary.empty(), stamp),
                Section.ok(List.of(), stamp),
                Section.ok(List.of(), stamp));
    }

    @Test
    void settingsRoutePassesTheAccessTokenCookieToTheUseCase() {
        when(getSettingsBffUseCase.execute(any(), any())).thenReturn(CompletableFuture.completedFuture(emptySettings()));

        webTestClient.get().uri("/api/v1/bff/settings")
                .header("X-User-Id", "1")
                .cookie("access_token", "header.payload.signature")
                .exchange()
                .expectStatus().isOk();

        verify(getSettingsBffUseCase).execute(new UserId(1L), Optional.of(new AccessToken("header.payload.signature")));
    }

    @Test
    void settingsRouteWithoutTheCookieStillAnswers() {
        when(getSettingsBffUseCase.execute(any(), any())).thenReturn(CompletableFuture.completedFuture(emptySettings()));

        webTestClient.get().uri("/api/v1/bff/settings")
                .header("X-User-Id", "1")
                .exchange()
                .expectStatus().isOk();

        verify(getSettingsBffUseCase).execute(new UserId(1L), Optional.empty());
    }

    @Test
    void investmentsRouteSerialisesTheSliceFiguresAndThePositionType() {
        ObservedAt stamp = ObservedAt.now(Clock.systemUTC());
        AssetTypeSlice bonds = new AssetTypeSlice("BOND", "BOND",
                MoneyFigure.of(new BigDecimal("904779.00"), Currency.ARS),
                MoneyFigure.of(new BigDecimal("986380.86"), Currency.ARS),
                MoneyFigure.of(new BigDecimal("-81601.86"), Currency.ARS),
                new BigDecimal("-8.27"), new BigDecimal("8.49"), 1);
        PositionRow row = new PositionRow(7L, "AO29", "Bono", new BigDecimal("687"), null, null, null, null,
                BigDecimal.ZERO, "017", "BOND");
        when(getInvestmentsBffUseCase.execute(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(new InvestmentsBffData(
                        Section.ok(List.of(), stamp), Section.ok(InvestmentsKpis.empty(), stamp),
                        Section.ok(List.of(), stamp), Section.ok(List.of(row), stamp),
                        Section.ok(List.of(bonds), stamp), Section.ok(List.of(), stamp),
                        Section.ok(List.of(), stamp))));

        webTestClient.get().uri("/api/v1/bff/investments")
                .header("X-User-Id", "1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.composition.data[0].label").isEqualTo("BOND")
                .jsonPath("$.data.composition.data[0].assetType").isEqualTo("BOND")
                .jsonPath("$.data.composition.data[0].amount.amount").isEqualTo("904779.00")
                .jsonPath("$.data.composition.data[0].cost.amount").isEqualTo("986380.86")
                .jsonPath("$.data.composition.data[0].pnl.amount").isEqualTo("-81601.86")
                .jsonPath("$.data.composition.data[0].pnlPct").isEqualTo(-8.27)
                .jsonPath("$.data.composition.data[0].pct").isEqualTo(8.49)
                .jsonPath("$.data.composition.data[0].count").isEqualTo(1)
                .jsonPath("$.data.positions.data[0].assetType").isEqualTo("BOND");
    }

    @Test
    void investmentsRouteReadsTheRangeAndDefaultsToOneMonth() {
        ObservedAt stamp = ObservedAt.now(Clock.systemUTC());
        InvestmentsBffData empty = new InvestmentsBffData(
                Section.ok(List.of(), stamp), Section.ok(InvestmentsKpis.empty(), stamp),
                Section.ok(List.of(), stamp), Section.ok(List.of(), stamp), Section.ok(List.of(), stamp),
                Section.ok(List.of(), stamp), Section.ok(List.of(), stamp));
        when(getInvestmentsBffUseCase.execute(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(empty));

        webTestClient.get().uri("/api/v1/bff/investments?range=1A").header("X-User-Id", "1")
                .exchange().expectStatus().isOk();
        webTestClient.get().uri("/api/v1/bff/investments").header("X-User-Id", "1")
                .exchange().expectStatus().isOk();
        webTestClient.get().uri("/api/v1/bff/investments?range=nonsense").header("X-User-Id", "1")
                .exchange().expectStatus().isOk();

        ArgumentCaptor<HistoryRange> ranges = ArgumentCaptor.forClass(HistoryRange.class);
        verify(getInvestmentsBffUseCase, times(3)).execute(any(), any(), any(), ranges.capture());
        assertThat(ranges.getAllValues())
                .containsExactly(HistoryRange.ONE_YEAR, HistoryRange.ONE_MONTH, HistoryRange.ONE_MONTH);
    }
}
