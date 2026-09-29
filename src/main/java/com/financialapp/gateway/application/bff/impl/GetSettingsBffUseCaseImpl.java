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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class GetSettingsBffUseCaseImpl implements GetSettingsBffUseCase {

    private static final String PROFILE_SOURCE = "ms-users profile";
    private static final String PREFERENCES_SOURCE = "ms-users preferences";
    private static final String BANK_FEES_SOURCE = "ms-banks user fees";
    private static final String BROKER_FEES_SOURCE = "ms-investments broker fees";
    private static final String NOTIFICATION_PREFERENCES_SOURCE = "ms-notifications preferences";
    private static final String SESSIONS_SOURCE = "ms-users sessions";
    private static final BigDecimal DEBIT_CREDIT_TAX_RATE = new BigDecimal("0.006");
    private static final String DEFAULT_IVA = "EXEMPT";

    private final UsersGateway users;
    private final BanksGateway banks;
    private final InvestmentsGateway investments;
    private final NotificationsGateway notifications;
    private final PageTimeoutBudget budget;
    private final Clock clock;

    @Autowired
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
                        users.fetchProfile(userId).thenApply(raw -> {
                            DownstreamPayload profile = new DownstreamPayload(PROFILE_SOURCE, raw);
                            return new UserProfile(
                                    profile.textOr("name", ""),
                                    profile.textOr("email", ""),
                                    profile.optionalInstant("createdAt").orElse(Instant.EPOCH));
                        }),
                        UserProfile.empty(), clock),
                UserProfile.empty());

        CompletableFuture<Section<UserPreferences>> preferencesSec = applyBudget(
                Section.guard(
                        users.fetchPreferences(userId).thenApply(raw -> {
                            DownstreamPayload preferences = new DownstreamPayload(PREFERENCES_SOURCE, raw);
                            return new UserPreferences(
                                    preferences.textOr("primaryCurrency", "ARS"),
                                    preferences.textOr("secondaryCurrency", "USD_MEP"),
                                    preferences.textOr("numberFormat", "1.234,56"),
                                    preferences.optionalLong("decimals").map(Long::intValue).orElse(2),
                                    preferences.flagOr("colorForAmounts", true));
                        }),
                        UserPreferences.empty(), clock),
                UserPreferences.empty());

        CompletableFuture<Section<FeesSummary>> feesSec = applyBudget(
                Section.guard(
                        banks.fetchFees(userId).thenCombine(investments.fetchBrokerFees(userId), (bankFees, brokerFees) -> {
                            DownstreamPayload fees = new DownstreamPayload(BANK_FEES_SOURCE, bankFees);
                            List<FeeRow> accounts = fees.list("accounts").stream()
                                    .map(account -> new FeeRow(null, account.text("cbu"),
                                            money(account.optionalDecimal("maintenanceFee"), account.text("currency")),
                                            null, account.textOr("ivaTreatment", DEFAULT_IVA)))
                                    .toList();
                            List<FeeRow> cards = fees.list("cards").stream()
                                    .map(card -> new FeeRow(null, card.text("cardNumber"),
                                            money(card.optionalDecimal("annualFee"), card.text("currency")),
                                            null, card.textOr("ivaTreatment", DEFAULT_IVA)))
                                    .toList();
                            List<FeeRow> brokers = DownstreamPayload.rows(BROKER_FEES_SOURCE, brokerFees).stream()
                                    .map(broker -> new FeeRow(null,
                                            broker.text("bankNumber") + " · " + broker.text("assetType"),
                                            null, broker.decimal("buyFeePct"), broker.textOr("ivaTreatment", DEFAULT_IVA)))
                                    .toList();
                            return new FeesSummary(accounts, cards, brokers, DEBIT_CREDIT_TAX_RATE);
                        }),
                        FeesSummary.empty(), clock),
                FeesSummary.empty());

        CompletableFuture<Section<List<NotificationPreference>>> notificationPrefsSec = applyBudget(
                Section.guard(
                        notifications.fetchNotificationPreferences(userId)
                                .thenApply(rows -> DownstreamPayload.rows(NOTIFICATION_PREFERENCES_SOURCE, rows).stream()
                                        .map(preference -> new NotificationPreference(preference.text("category"), channelsOf(preference)))
                                        .toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<SessionRow>>> sessionsSec = applyBudget(
                Section.guard(
                        users.fetchSessions(userId)
                                .thenApply(rows -> DownstreamPayload.rows(SESSIONS_SOURCE, rows).stream()
                                        .map(session -> new SessionRow(
                                                session.text("id"),
                                                session.textOr("device", ""),
                                                session.instant("lastSeenAt"),
                                                session.flagOr("current", false)))
                                        .toList()),
                        List.of(), clock),
                List.of());

        return CompletableFuture.allOf(profileSec, preferencesSec, feesSec, notificationPrefsSec, sessionsSec)
                .thenApply(v -> new SettingsBffData(
                        profileSec.join(), preferencesSec.join(), feesSec.join(),
                        notificationPrefsSec.join(), sessionsSec.join()));
    }

    private static MoneyFigure money(Optional<BigDecimal> amount, String currency) {
        return amount.map(value -> MoneyFigure.of(value, Currency.of(currency))).orElse(null);
    }

    private static List<String> channelsOf(DownstreamPayload preference) {
        List<String> channels = new ArrayList<>();
        if (preference.flag("inAppEnabled")) {
            channels.add("IN_APP");
        }
        if (preference.flag("emailEnabled")) {
            channels.add("EMAIL");
        }
        return List.copyOf(channels);
    }

    private <T> CompletableFuture<Section<T>> applyBudget(CompletableFuture<Section<T>> sectionFuture, T fallback) {
        return sectionFuture.completeOnTimeout(
                Section.unavailable(fallback, ObservedAt.now(clock)),
                budget.total().toMillis(),
                TimeUnit.MILLISECONDS);
    }
}
