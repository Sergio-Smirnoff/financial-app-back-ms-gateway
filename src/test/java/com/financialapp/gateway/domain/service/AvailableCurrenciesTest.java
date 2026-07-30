package com.financialapp.gateway.domain.service;

import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.service.AvailableCurrencies.AvailableCurrenciesResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AvailableCurrenciesTest {

    private final AvailableCurrencies service = new AvailableCurrencies();

    @Test
    void always_includes_ars_and_deduplicates_currencies() {
        List<Currency> accounts = List.of(Currency.ARS, Currency.USD);
        List<Currency> holdings = List.of(Currency.USD, Currency.EUR);

        AvailableCurrenciesResult result = service.resolve(accounts, holdings, Currency.ARS);

        assertThat(result.available()).containsExactly(Currency.ARS, Currency.USD, Currency.EUR);
        assertThat(result.defaultCurrency()).isEqualTo(Currency.ARS);
    }

    @Test
    void preferred_eur_held_results_in_default_eur() {
        List<Currency> accounts = List.of(Currency.ARS);
        List<Currency> holdings = List.of(Currency.EUR);

        AvailableCurrenciesResult result = service.resolve(accounts, holdings, Currency.EUR);

        assertThat(result.available()).contains(Currency.EUR);
        assertThat(result.defaultCurrency()).isEqualTo(Currency.EUR);
    }

    @Test
    void preferred_eur_not_held_falls_back_to_default_ars() {
        List<Currency> accounts = List.of(Currency.ARS);
        List<Currency> holdings = List.of(Currency.USD);

        AvailableCurrenciesResult result = service.resolve(accounts, holdings, Currency.EUR);

        assertThat(result.available()).containsExactly(Currency.ARS, Currency.USD);
        assertThat(result.defaultCurrency()).isEqualTo(Currency.ARS);
    }

    @Test
    void handles_null_lists_gracefully() {
        AvailableCurrenciesResult result = service.resolve(null, null, null);

        assertThat(result.available()).containsExactly(Currency.ARS);
        assertThat(result.defaultCurrency()).isEqualTo(Currency.ARS);
    }
}
