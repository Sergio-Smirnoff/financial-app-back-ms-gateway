package com.financialapp.gateway.domain.service;

import com.financialapp.gateway.domain.model.currency.Currency;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AvailableCurrencies {

    public record AvailableCurrenciesResult(Set<Currency> available, Currency defaultCurrency) {}

    public AvailableCurrenciesResult resolve(
            List<Currency> accountCurrencies,
            List<Currency> holdingCurrencies,
            Currency preferredDefault) {

        Set<Currency> available = new LinkedHashSet<>();
        available.add(Currency.ARS);

        if (accountCurrencies != null) {
            available.addAll(accountCurrencies);
        }
        if (holdingCurrencies != null) {
            available.addAll(holdingCurrencies);
        }

        Currency defaultCurrency = (preferredDefault != null && available.contains(preferredDefault))
                ? preferredDefault
                : Currency.ARS;

        return new AvailableCurrenciesResult(available, defaultCurrency);
    }
}
