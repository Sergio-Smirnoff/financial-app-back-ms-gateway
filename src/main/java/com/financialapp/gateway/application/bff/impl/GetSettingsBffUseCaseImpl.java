package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.gateway.NotificationsGateway;
import com.financialapp.gateway.domain.gateway.UsersGateway;
import com.financialapp.gateway.domain.model.bff.SettingsBffData;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.usecase.bff.GetSettingsBffUseCase;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class GetSettingsBffUseCaseImpl implements GetSettingsBffUseCase {

    private final UsersGateway users;
    private final BanksGateway banks;
    private final InvestmentsGateway investments;
    private final NotificationsGateway notifications;
    private final PageTimeoutBudget budget;
    private final Clock clock;

    public GetSettingsBffUseCaseImpl(
            UsersGateway users, BanksGateway banks,
            InvestmentsGateway investments, NotificationsGateway notifications,
            PageTimeoutBudget budget) {
        this(users, banks, investments, notifications, budget, Clock.systemUTC());
    }

    public GetSettingsBffUseCaseImpl(
            UsersGateway users, BanksGateway banks,
            InvestmentsGateway investments, NotificationsGateway notifications,
            PageTimeoutBudget budget, Clock clock) {
        this.users = users;
        this.banks = banks;
        this.investments = investments;
        this.notifications = notifications;
        this.budget = budget != null ? budget : PageTimeoutBudget.fromMillis(5000);
        this.clock = clock;
    }

    @Override
    public CompletableFuture<SettingsBffData> execute(UserId userId) {
        CompletableFuture<Section<Map<String, Object>>> profile = applyBudget(
                Section.guard(users.fetchProfile(userId), Map.of(), clock), Map.of());

        CompletableFuture<Section<Map<String, Object>>> preferences = applyBudget(
                Section.guard(users.fetchPreferences(userId), Map.of(), clock), Map.of());

        CompletableFuture<Map<String, Object>> feesFuture = banks.fetchFees(userId)
                .thenCombine(investments.fetchBrokerFees(userId), (bankFees, brokerFees) -> {
                    Map<String, Object> res = new HashMap<>();
                    res.put("bankFees", bankFees);
                    res.put("brokerFees", brokerFees);
                    return res;
                });

        CompletableFuture<Section<Map<String, Object>>> fees = applyBudget(
                Section.guard(feesFuture, Map.of(), clock), Map.of());

        CompletableFuture<Section<List<Map<String, Object>>>> notificationPrefs = applyBudget(
                Section.guard(notifications.fetchNotificationPreferences(userId), List.of(), clock), List.of());

        CompletableFuture<Section<List<Map<String, Object>>>> sessions = applyBudget(
                Section.guard(users.fetchSessions(userId), List.of(), clock), List.of());

        return CompletableFuture.allOf(profile, preferences, fees, notificationPrefs, sessions)
                .thenApply(v -> new SettingsBffData(
                        profile.join(), preferences.join(), fees.join(), notificationPrefs.join(), sessions.join()));
    }

    private <T> CompletableFuture<Section<T>> applyBudget(CompletableFuture<Section<T>> sectionFuture, T fallback) {
        return sectionFuture.completeOnTimeout(
                Section.unavailable(fallback, ObservedAt.now(clock)),
                budget.total().toMillis(),
                TimeUnit.MILLISECONDS);
    }
}
