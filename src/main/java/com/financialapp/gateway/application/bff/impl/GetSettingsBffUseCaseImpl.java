package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.gateway.NotificationsGateway;
import com.financialapp.gateway.domain.gateway.UsersGateway;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.*;
import com.financialapp.gateway.domain.model.bff.MoneyFigure;
import com.financialapp.gateway.domain.model.bff.SettingsBffData;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.usecase.bff.GetSettingsBffUseCase;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
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
        CompletableFuture<Section<UserProfile>> profileSec = applyBudget(
                Section.guard(
                        users.fetchProfile(userId)
                                .thenApply(p -> {
                                    String name = String.valueOf(p.getOrDefault("name", ""));
                                    String email = String.valueOf(p.getOrDefault("email", ""));
                                    Instant createdAt = parseInstant(p.get("createdAt"));
                                    return new UserProfile(name, email, createdAt);
                                }),
                        UserProfile.empty(), clock),
                UserProfile.empty());

        CompletableFuture<Section<UserPreferences>> preferencesSec = applyBudget(
                Section.guard(
                        users.fetchPreferences(userId)
                                .thenApply(p -> {
                                    String primaryCurrency = String.valueOf(p.getOrDefault("primaryCurrency", "ARS"));
                                    String secondaryCurrency = String.valueOf(p.getOrDefault("secondaryCurrency", "USD_MEP"));
                                    String numberFormat = String.valueOf(p.getOrDefault("numberFormat", "1.234,56"));
                                    Integer decimals = parseInt(p.get("decimals"), 2);
                                    Boolean useGainLossColors = Boolean.TRUE.equals(p.getOrDefault("colorForAmounts", true));
                                    return new UserPreferences(primaryCurrency, secondaryCurrency, numberFormat, decimals, useGainLossColors);
                                }),
                        UserPreferences.empty(), clock),
                UserPreferences.empty());

        CompletableFuture<Section<FeesSummary>> feesSec = applyBudget(
                Section.guard(
                        banks.fetchFees(userId)
                                .thenCombine(investments.fetchBrokerFees(userId), (bankFees, brokerFees) -> {
                                    List<FeeRow> accounts = bankFees.stream()
                                            .filter(f -> "ACCOUNT".equalsIgnoreCase(String.valueOf(f.get("scope"))))
                                            .map(this::mapFeeRow).toList();
                                    List<FeeRow> cards = bankFees.stream()
                                            .filter(f -> "CARD".equalsIgnoreCase(String.valueOf(f.get("scope"))))
                                            .map(this::mapFeeRow).toList();
                                    List<FeeRow> brokers = brokerFees.stream().map(this::mapFeeRow).toList();
                                    BigDecimal taxRate = new BigDecimal("0.006"); // 0.6% tax
                                    return new FeesSummary(accounts, cards, brokers, taxRate);
                                }),
                        FeesSummary.empty(), clock),
                FeesSummary.empty());

        CompletableFuture<Section<List<NotificationPreference>>> notificationPrefsSec = applyBudget(
                Section.guard(
                        notifications.fetchNotificationPreferences(userId)
                                .thenApply(list -> list.stream().map(n -> {
                                    String cat = String.valueOf(n.getOrDefault("category", ""));
                                    Object chObj = n.get("channels");
                                    List<String> channels = chObj instanceof List<?> l ? (List<String>) l : List.of("EMAIL");
                                    return new NotificationPreference(cat, channels);
                                }).toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<SessionRow>>> sessionsSec = applyBudget(
                Section.guard(
                        users.fetchSessions(userId)
                                .thenApply(list -> list.stream().map(s -> {
                                    String id = String.valueOf(s.getOrDefault("id", ""));
                                    String device = String.valueOf(s.getOrDefault("device", ""));
                                    String ip = String.valueOf(s.getOrDefault("ip", ""));
                                    Instant lastSeenAt = parseInstant(s.get("lastSeenAt"));
                                    Boolean current = Boolean.TRUE.equals(s.get("current"));
                                    return new SessionRow(id, device, ip, lastSeenAt, current);
                                }).toList()),
                        List.of(), clock),
                List.of());

        return CompletableFuture.allOf(profileSec, preferencesSec, feesSec, notificationPrefsSec, sessionsSec)
                .thenApply(v -> new SettingsBffData(
                        profileSec.join(), preferencesSec.join(), feesSec.join(),
                        notificationPrefsSec.join(), sessionsSec.join()));
    }

    private FeeRow mapFeeRow(Map<String, Object> map) {
        String scope = String.valueOf(map.getOrDefault("scope", "GENERAL"));
        String label = String.valueOf(map.getOrDefault("label", map.getOrDefault("name", "")));
        Object amtObj = map.get("amount");
        MoneyFigure amount = amtObj != null ? MoneyFigure.of(parseDecimal(amtObj), Currency.ARS) : null;
        Object pctObj = map.get("pct");
        BigDecimal pct = pctObj != null ? parseDecimal(pctObj) : null;
        String ivaTreatment = String.valueOf(map.getOrDefault("ivaTreatment", "EXEMPT"));
        return new FeeRow(scope, label, amount, pct, ivaTreatment);
    }

    private <T> CompletableFuture<Section<T>> applyBudget(CompletableFuture<Section<T>> sectionFuture, T fallback) {
        return sectionFuture.completeOnTimeout(
                Section.unavailable(fallback, ObservedAt.now(clock)),
                budget.total().toMillis(),
                TimeUnit.MILLISECONDS);
    }

    private static BigDecimal parseDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        try { return new BigDecimal(val.toString()); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private static int parseInt(Object val, int fallback) {
        if (val == null) return fallback;
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return fallback; }
    }

    private static Instant parseInstant(Object val) {
        if (val == null) return Instant.EPOCH;
        try { return Instant.parse(val.toString()); } catch (Exception e) { return Instant.EPOCH; }
    }
}
