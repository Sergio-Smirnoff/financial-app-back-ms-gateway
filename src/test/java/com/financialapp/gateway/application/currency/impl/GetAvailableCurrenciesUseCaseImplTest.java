package com.financialapp.gateway.application.currency.impl;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.gateway.UsersGateway;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.currency.UserDisplayPreferences;
import com.financialapp.gateway.domain.service.AvailableCurrencies.AvailableCurrenciesResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetAvailableCurrenciesUseCaseImplTest {

    private final BanksGateway banks = mock(BanksGateway.class);
    private final InvestmentsGateway investments = mock(InvestmentsGateway.class);
    private final UsersGateway users = mock(UsersGateway.class);
    private final GetAvailableCurrenciesUseCaseImpl useCase = new GetAvailableCurrenciesUseCaseImpl(banks, investments, users);

    private final UserId user = new UserId(42L);

    @Test
    void composes_available_currencies_from_all_gateways() {
        when(banks.accountCurrencies(user))
                .thenReturn(CompletableFuture.completedFuture(List.of(Currency.ARS, Currency.USD)));
        when(investments.holdingCurrencies(42L))
                .thenReturn(CompletableFuture.completedFuture(List.of(Currency.EUR)));
        when(users.displayPreferences(42L))
                .thenReturn(CompletableFuture.completedFuture(
                        new UserDisplayPreferences(Currency.EUR, null, "1.234,56", 2, true)));

        AvailableCurrenciesResult result = useCase.execute(user).join();

        assertThat(result.available()).containsExactly(Currency.ARS, Currency.USD, Currency.EUR);
        assertThat(result.defaultCurrency()).isEqualTo(Currency.EUR);
    }

    @Test
    void degrades_gracefully_when_one_downstream_fails() {
        when(banks.accountCurrencies(user))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("banks down")));
        when(investments.holdingCurrencies(42L))
                .thenReturn(CompletableFuture.completedFuture(List.of(Currency.USD)));
        when(users.displayPreferences(42L))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("users down")));

        AvailableCurrenciesResult result = useCase.execute(user).join();

        assertThat(result.available()).containsExactly(Currency.ARS, Currency.USD);
        assertThat(result.defaultCurrency()).isEqualTo(Currency.ARS);
    }
}
