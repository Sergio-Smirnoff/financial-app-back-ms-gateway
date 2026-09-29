package com.financialapp.gateway.application.bff;

import com.financialapp.gateway.application.bff.impl.GetSettingsBffUseCaseImpl;
import com.financialapp.gateway.contracts.DownstreamFixtures;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.gateway.NotificationsGateway;
import com.financialapp.gateway.domain.gateway.UsersGateway;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.FeeRow;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.FeesSummary;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.NotificationPreference;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.SessionRow;
import com.financialapp.gateway.domain.model.bff.SettingsBffData;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.SectionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettingsBffTest {

    @Mock private UsersGateway users;
    @Mock private BanksGateway banks;
    @Mock private InvestmentsGateway investments;
    @Mock private NotificationsGateway notifications;

    private GetSettingsBffUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetSettingsBffUseCaseImpl(users, banks, investments, notifications, PageTimeoutBudget.fromMillis(3000));
        lenient().when(users.fetchProfile(any())).thenReturn(CompletableFuture.completedFuture(Map.of("email", "user@test.com")));
        lenient().when(users.fetchPreferences(any())).thenReturn(CompletableFuture.completedFuture(Map.of("theme", "DARK")));
        lenient().when(banks.fetchFees(any())).thenReturn(CompletableFuture.completedFuture(
                DownstreamFixtures.object("banks/user-fees.json")));
        lenient().when(investments.fetchBrokerFees(any())).thenReturn(CompletableFuture.completedFuture(
                DownstreamFixtures.list("investments/broker-fees.json")));
        lenient().when(notifications.fetchNotificationPreferences(any())).thenReturn(CompletableFuture.completedFuture(
                DownstreamFixtures.list("notifications/preferences-by-category.json")));
        lenient().when(users.fetchSessions(any())).thenReturn(CompletableFuture.completedFuture(
                DownstreamFixtures.list("users/sessions.json")));
    }

    private SettingsBffData settings() {
        return useCase.execute(new UserId(1L)).join();
    }

    @Test
    void execute_returnsOkSectionsForSettings() {
        SettingsBffData data = settings();

        assertThat(data.profile().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.preferences().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.fees().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.notificationPrefs().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.sessions().status()).isEqualTo(SectionStatus.OK);
    }

    @Test
    void feesReadTheUserFeesObjectAndTheBrokerSchedules() {
        FeesSummary fees = settings().fees().data();

        FeeRow account = fees.accounts().get(0);
        assertThat(account.label()).isEqualTo("0170099200000000000017");
        assertThat(account.amount().amount()).isEqualByComparingTo("5500.00");
        assertThat(account.amount().currency().getCurrencyCode()).isEqualTo("ARS");
        assertThat(account.ivaTreatment()).isEqualTo("TAXED_21");
        assertThat(account.scope()).isNull();
        FeeRow card = fees.cards().get(0);
        assertThat(card.label()).isEqualTo("4509953566233704");
        assertThat(card.amount().amount()).isEqualByComparingTo("48000.00");
        FeeRow broker = fees.brokers().get(0);
        assertThat(broker.label()).isEqualTo("017 · BOND");
        assertThat(broker.amount()).isNull();
        assertThat(broker.pct()).isEqualByComparingTo("0.5");
    }

    @Test
    void anEmptyFeesPayloadIsUnavailable() {
        when(banks.fetchFees(any())).thenReturn(CompletableFuture.completedFuture(Map.of()));

        assertThat(settings().fees().status()).isEqualTo(SectionStatus.UNAVAILABLE);
    }

    @Test
    void channelsFollowTheInAppAndEmailFlags() {
        List<NotificationPreference> preferences = settings().notificationPrefs().data();

        assertThat(preferences).extracting(NotificationPreference::category, NotificationPreference::channels)
                .containsExactly(
                        tuple("PORTFOLIO_ALERTS", List.of("IN_APP")),
                        tuple("SUMMARY", List.of("EMAIL")));
    }

    @Test
    void sessionsReadTheLocalDateTimeLastSeen() {
        SessionRow session = settings().sessions().data().get(0);

        assertThat(session.id()).isEqualTo("12");
        assertThat(session.device()).isEqualTo("Firefox en Linux");
        assertThat(session.lastSeenAt()).isEqualTo(Instant.parse("2026-09-28T11:15:00Z"));
        assertThat(session.current()).isFalse();
    }
}
