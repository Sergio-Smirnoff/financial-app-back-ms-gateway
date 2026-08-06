package com.financialapp.gateway.infrastructure.gateway.Impl;

import com.financialapp.gateway.domain.common.model.TimeoutPolicy;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.dashboard.LoanView;
import com.financialapp.gateway.domain.model.dashboard.UpcomingPaymentView;
import com.financialapp.gateway.infrastructure.config.ServicesProperties;
import com.financialapp.gateway.infrastructure.gateway.dto.GatewayApiResponse;
import com.financialapp.gateway.infrastructure.gateway.dto.LoanResponse;
import com.financialapp.gateway.infrastructure.gateway.dto.UpcomingPaymentResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class BanksGatewayImpl implements BanksGateway {

    private static final ParameterizedTypeReference<GatewayApiResponse<List<LoanResponse>>> LOANS_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<GatewayApiResponse<List<UpcomingPaymentResponse>>> UPCOMING_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<GatewayApiResponse<List<String>>> CURRENCIES_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<GatewayApiResponse<List<Map<String, Object>>>> LIST_MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;
    private final String banksUrl;
    private final TimeoutPolicy timeoutPolicy;

    public BanksGatewayImpl(WebClient internalWebClient, ServicesProperties services, TimeoutPolicy timeoutPolicy) {
        this.webClient = internalWebClient;
        this.banksUrl = services.getBanksUrl();
        this.timeoutPolicy = timeoutPolicy;
    }

    @Override
    public CompletableFuture<List<LoanView>> fetchActiveLoans(UserId userId) {
        return webClient.get()
                .uri(banksUrl + "/api/v1/banks/loans?active=true")
                .header("X-User-Id", userId.value().toString())
                .retrieve()
                .bodyToMono(LOANS_TYPE)
                .map(r -> r.data() == null ? List.<LoanView>of() : r.data().stream()
                        .filter(LoanResponse::active)
                        .map(this::toLoanView)
                        .toList())
                .timeout(timeoutPolicy.perCall())
                .toFuture();
    }

    @Override
    public CompletableFuture<List<UpcomingPaymentView>> fetchUpcomingPayments(UserId userId, LocalDate from, LocalDate to) {
        return webClient.get()
                .uri(banksUrl + "/api/v1/banks/upcoming-payments?from={from}&to={to}", from, to)
                .header("X-User-Id", userId.value().toString())
                .retrieve()
                .bodyToMono(UPCOMING_TYPE)
                .map(r -> r.data() == null ? List.<UpcomingPaymentView>of() : r.data().stream().map(this::toUpcomingView).toList())
                .timeout(timeoutPolicy.perCall())
                .toFuture();
    }

    @Override
    public CompletableFuture<List<Currency>> accountCurrencies(UserId userId) {
        return webClient.get()
                .uri(banksUrl + "/api/v1/banks/accounts/currencies")
                .header("X-User-Id", userId.value().toString())
                .retrieve()
                .bodyToMono(CURRENCIES_TYPE)
                .map(r -> r.data() == null ? List.<Currency>of() : r.data().stream().map(Currency::new).toList())
                .timeout(timeoutPolicy.perCall())
                .toFuture();
    }

    @Override
    public CompletableFuture<List<Map<String, Object>>> fetchAccounts(UserId userId) {
        return webClient.get()
                .uri(banksUrl + "/api/v1/banks/accounts")
                .header("X-User-Id", userId.value().toString())
                .retrieve()
                .bodyToMono(LIST_MAP_TYPE)
                .map(r -> r.data() != null ? r.data() : List.<Map<String, Object>>of())
                .onErrorReturn(List.of())
                .timeout(timeoutPolicy.perCall())
                .toFuture();
    }

    @Override
    public CompletableFuture<List<Map<String, Object>>> fetchCards(UserId userId) {
        return webClient.get()
                .uri(banksUrl + "/api/v1/banks/cards")
                .header("X-User-Id", userId.value().toString())
                .retrieve()
                .bodyToMono(LIST_MAP_TYPE)
                .map(r -> r.data() != null ? r.data() : List.<Map<String, Object>>of())
                .onErrorReturn(List.of())
                .timeout(timeoutPolicy.perCall())
                .toFuture();
    }

    @Override
    public CompletableFuture<List<Map<String, Object>>> fetchBalanceSnapshots(UserId userId, LocalDate from, LocalDate to) {
        return webClient.get()
                .uri(banksUrl + "/api/v1/banks/balance-snapshots?from={from}&to={to}", from, to)
                .header("X-User-Id", userId.value().toString())
                .retrieve()
                .bodyToMono(LIST_MAP_TYPE)
                .map(r -> r.data() != null ? r.data() : List.<Map<String, Object>>of())
                .onErrorReturn(List.of())
                .timeout(timeoutPolicy.perCall())
                .toFuture();
    }

    @Override
    public CompletableFuture<List<Map<String, Object>>> fetchLoans(UserId userId) {
        return webClient.get()
                .uri(banksUrl + "/api/v1/banks/loans")
                .header("X-User-Id", userId.value().toString())
                .retrieve()
                .bodyToMono(LIST_MAP_TYPE)
                .map(r -> r.data() != null ? r.data() : List.<Map<String, Object>>of())
                .onErrorReturn(List.of())
                .timeout(timeoutPolicy.perCall())
                .toFuture();
    }

    @Override
    public CompletableFuture<List<Map<String, Object>>> fetchFees(UserId userId) {
        return webClient.get()
                .uri(banksUrl + "/api/v1/banks/fees")
                .header("X-User-Id", userId.value().toString())
                .retrieve()
                .bodyToMono(LIST_MAP_TYPE)
                .map(r -> r.data() != null ? r.data() : List.<Map<String, Object>>of())
                .onErrorReturn(List.of())
                .timeout(timeoutPolicy.perCall())
                .toFuture();
    }

    private LoanView toLoanView(LoanResponse dto) {
        return new LoanView(
                dto.id(), dto.name(), dto.currency(), dto.principal(),
                dto.totalInstallments(), dto.remainingInstallments(), dto.active());
    }

    private UpcomingPaymentView toUpcomingView(UpcomingPaymentResponse dto) {
        return new UpcomingPaymentView(
                dto.id(), dto.type(), dto.description(), dto.amount(), dto.currency(),
                dto.dueDate(), dto.installmentNumber(), dto.totalInstallments(), dto.paid());
    }
}
