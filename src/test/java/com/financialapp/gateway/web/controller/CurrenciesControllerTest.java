package com.financialapp.gateway.web.controller;

import com.financialapp.commons.core.response.ApiResponse;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.service.AvailableCurrencies.AvailableCurrenciesResult;
import com.financialapp.gateway.domain.usecase.currency.GetAvailableCurrencies;
import com.financialapp.gateway.web.dto.response.AvailableCurrenciesResponse;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrenciesControllerTest {

    private final GetAvailableCurrencies useCase = mock(GetAvailableCurrencies.class);
    private final CurrenciesController controller = new CurrenciesController(useCase);

    @Test
    void returns_api_response_with_available_currencies() {
        AvailableCurrenciesResult result = new AvailableCurrenciesResult(
                Set.of(Currency.ARS, Currency.USD, Currency.EUR), Currency.ARS);
        when(useCase.execute(new UserId(10L)))
                .thenReturn(CompletableFuture.completedFuture(result));

        ApiResponse<AvailableCurrenciesResponse> response = controller.getCurrencies(10L).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().available()).containsExactlyInAnyOrder("ARS", "USD", "EUR");
        assertThat(response.getData().defaultCurrency()).isEqualTo("ARS");
    }
}
