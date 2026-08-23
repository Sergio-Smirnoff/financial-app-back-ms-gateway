package com.financialapp.gateway.application.currency.impl;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.gateway.UsersGateway;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.currency.UserDisplayPreferences;
import com.financialapp.gateway.domain.service.AvailableCurrencies;
import com.financialapp.gateway.domain.service.AvailableCurrencies.AvailableCurrenciesResult;
import com.financialapp.gateway.domain.usecase.currency.GetAvailableCurrencies;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class GetAvailableCurrenciesUseCaseImpl implements GetAvailableCurrencies {

    private final BanksGateway banks;
    private final InvestmentsGateway investments;
    private final UsersGateway users;
    private final AvailableCurrencies service = new AvailableCurrencies();
    private final Clock clock;

    @Autowired
    public GetAvailableCurrenciesUseCaseImpl(BanksGateway banks, InvestmentsGateway investments, UsersGateway users) {
        this(banks, investments, users, Clock.systemUTC());
    }

    public GetAvailableCurrenciesUseCaseImpl(
            BanksGateway banks, InvestmentsGateway investments, UsersGateway users, Clock clock) {
        this.banks = banks;
        this.investments = investments;
        this.users = users;
        this.clock = clock;
    }

    @Override
    public CompletableFuture<AvailableCurrenciesResult> execute(UserId userId) {
        CompletableFuture<Section<List<Currency>>> accountsFuture =
                Section.guard(banks.accountCurrencies(userId), List.of(), clock);
        CompletableFuture<Section<List<Currency>>> holdingsFuture =
                Section.guard(investments.holdingCurrencies(userId.value()), List.of(), clock);
        CompletableFuture<Section<UserDisplayPreferences>> prefFuture =
                Section.guard(users.displayPreferences(userId.value()),
                        new UserDisplayPreferences(Currency.ARS, null, "1.234,56", 2, true), clock);

        return CompletableFuture.allOf(accountsFuture, holdingsFuture, prefFuture)
                .thenApply(ignored -> {
                    List<Currency> accounts = accountsFuture.join().data();
                    List<Currency> holdings = holdingsFuture.join().data();
                    Currency preferred = prefFuture.join().data().primaryCurrency();
                    return service.resolve(accounts, holdings, preferred);
                });
    }
}
