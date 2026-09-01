package com.financialapp.gateway.application.bff;

import com.financialapp.gateway.application.bff.impl.GetSettingsBffUseCaseImpl;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.gateway.NotificationsGateway;
import com.financialapp.gateway.domain.gateway.UsersGateway;
import com.financialapp.gateway.domain.model.bff.SettingsBffData;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.SectionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    }

    @Test
    void execute_returnsOkSectionsForSettings() {
        when(users.fetchProfile(any())).thenReturn(CompletableFuture.completedFuture(Map.of("email", "user@test.com")));
        when(users.fetchPreferences(any())).thenReturn(CompletableFuture.completedFuture(Map.of("theme", "DARK")));
        when(banks.fetchFees(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(investments.fetchBrokerFees(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(notifications.fetchNotificationPreferences(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(users.fetchSessions(any())).thenReturn(CompletableFuture.completedFuture(List.of()));

        SettingsBffData data = useCase.execute(new UserId(1L)).join();

        assertThat(data.profile().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.preferences().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.fees().status()).isEqualTo(SectionStatus.OK);
    }
}
