package com.financialapp.gateway.infrastructure.gateway.Impl;

import com.financialapp.gateway.domain.common.model.AccessToken;
import com.financialapp.gateway.domain.common.model.TimeoutPolicy;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.currency.FxRateMode;
import com.financialapp.gateway.domain.model.currency.ManualCurrencyRate;
import com.financialapp.gateway.domain.model.currency.UserDisplayPreferences;
import com.financialapp.gateway.infrastructure.config.ServicesProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class UsersGatewayImplTest {

    private UsersGatewayImpl gatewayReturning(String json) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .body(json)
                        .build()))
                .build();
        ServicesProperties services = new ServicesProperties();
        services.setUsersUrl("http://users.test");
        return new UsersGatewayImpl(webClient, services, new TimeoutPolicy(Duration.ofSeconds(5)));
    }

    @Test
    void maps_user_preferences() {
        String json = """
            {
              "success": true,
              "data": {
                "maxIdleMinutes": 15,
                "timezone": "America/Argentina/Buenos_Aires",
                "primaryCurrency": "USD",
                "secondaryCurrency": "USD_MEP",
                "numberFormat": "1.234,56",
                "decimals": 2,
                "colorForAmounts": true
              }
            }
            """;
        UserDisplayPreferences result = gatewayReturning(json).displayPreferences(42L).join();
        assertThat(result.primaryCurrency()).isEqualTo(Currency.USD);
        assertThat(result.secondaryMode()).isEqualTo(FxRateMode.MEP);
        assertThat(result.numberFormat()).isEqualTo("1.234,56");
        assertThat(result.decimals()).isEqualTo(2);
        assertThat(result.colorForAmounts()).isTrue();
    }

    @Test
    void maps_manual_currency_rates() {
        String json = """
            {
              "success": true,
              "data": [
                { "currency": "EUR", "ratePerArs": "1200.00", "updatedAt": "2026-07-30T10:00:00Z" }
              ]
            }
            """;
        List<ManualCurrencyRate> result = gatewayReturning(json).manualCurrencyRates(42L).join();
        assertThat(result).containsExactly(new ManualCurrencyRate(Currency.EUR, new BigDecimal("1200.00")));
    }

    @Test
    void fetchProfileCallsTheRealUsersPath() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    assertThat(request.url().getPath()).isEqualTo("/api/v1/users/me/profile");
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .body("""
                                    {"success":true,"data":{"name":"Ana","email":"ana@example.com","createdAt":"2026-01-01T00:00:00Z"}}""")
                            .build());
                })
                .build();
        ServicesProperties services = new ServicesProperties();
        services.setUsersUrl("http://users.test");
        UsersGatewayImpl gateway = new UsersGatewayImpl(webClient, services, new TimeoutPolicy(Duration.ofSeconds(5)));

        Map<String, Object> profile = gateway.fetchProfile(new UserId(1L)).join();
        assertThat(profile.get("email")).isEqualTo("ana@example.com");
    }

    private UsersGatewayImpl gatewayRecording(AtomicReference<ClientRequest> sent) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    sent.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .body("""
                                    {"status":200,"data":[{"id":13,"device":"Chrome en Linux","current":true,"rememberMe":false,"createdAt":"2026-09-29T09:00:00","lastSeenAt":"2026-09-29T09:30:00"}]}""")
                            .build());
                })
                .build();
        ServicesProperties services = new ServicesProperties();
        services.setUsersUrl("http://users.test");
        return new UsersGatewayImpl(webClient, services, new TimeoutPolicy(Duration.ofSeconds(5)));
    }

    @Test
    void fetchSessionsForwardsTheCallersAccessTokenCookie() {
        AtomicReference<ClientRequest> sent = new AtomicReference<>();

        List<Map<String, Object>> sessions = gatewayRecording(sent)
                .fetchSessions(new UserId(1L), Optional.of(new AccessToken("header.payload.signature"))).join();

        assertThat(sent.get().url().getPath()).isEqualTo("/api/v1/users/me/sessions");
        assertThat(sent.get().headers().getFirst("X-User-Id")).isEqualTo("1");
        assertThat(sent.get().cookies().getFirst("access_token")).isEqualTo("header.payload.signature");
        assertThat(sessions).singleElement().satisfies(session -> assertThat(session.get("current")).isEqualTo(true));
    }

    @Test
    void fetchSessionsSendsNoCookieWhenTheCallerHasNoToken() {
        AtomicReference<ClientRequest> sent = new AtomicReference<>();

        gatewayRecording(sent).fetchSessions(new UserId(1L), Optional.empty()).join();

        assertThat(sent.get().cookies()).doesNotContainKey("access_token");
    }
}
