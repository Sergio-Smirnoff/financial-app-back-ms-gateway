package com.financialapp.gateway.infrastructure.gateway.Impl;

import com.financialapp.gateway.domain.common.model.TimeoutPolicy;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.BanksGateway;
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
import java.util.concurrent.CompletableFuture;

@Component
public class BanksGatewayImpl implements BanksGateway {

    private static final ParameterizedTypeReference<GatewayApiResponse<List<LoanResponse>>> LOANS_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<GatewayApiResponse<List<UpcomingPaymentResponse>>> PAYMENTS_TYPE =
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
                .uri(banksUrl + "/api/v1/banks/loans")
                .header("X-User-Id", userId.value().toString())
                .retrieve()
                .bodyToMono(LOANS_TYPE)
                .map(response -> nullSafe(response.data()).stream()
                        .filter(LoanResponse::active)
                        .map(l -> new LoanView(
                                l.id(), l.name(), l.currency(), l.principal(),
                                l.totalInstallments(), l.remainingInstallments(), l.active()))
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
                .bodyToMono(PAYMENTS_TYPE)
                .map(response -> nullSafe(response.data()).stream()
                        .map(p -> new UpcomingPaymentView(
                                p.id(), p.type(), p.description(), p.amount(), p.currency(), p.dueDate(),
                                p.installmentNumber(), p.totalInstallments(), p.paid()))
                        .toList())
                .timeout(timeoutPolicy.perCall())
                .toFuture();
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
