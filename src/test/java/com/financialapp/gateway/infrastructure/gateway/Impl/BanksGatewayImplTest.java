package com.financialapp.gateway.infrastructure.gateway.Impl;

import com.financialapp.gateway.domain.common.model.TimeoutPolicy;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.bff.LoanView;
import com.financialapp.gateway.domain.model.bff.UpcomingPaymentView;
import com.financialapp.gateway.infrastructure.config.ServicesProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BanksGatewayImplTest {

    private BanksGatewayImpl gatewayReturning(String json) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .body(json)
                        .build()))
                .build();
        ServicesProperties services = new ServicesProperties();
        services.setBanksUrl("http://banks.test");
        return new BanksGatewayImpl(webClient, services, new TimeoutPolicy(Duration.ofSeconds(5)));
    }

    @Test
    void keeps_only_active_loans_and_maps_progress_fields() {
        String json = """
            { "success": true, "data": [
              { "id": 1, "name": "Car", "currency": "ARS", "principal": "50000.00",
                "totalInstallments": 12, "remainingInstallments": 9, "active": true },
              { "id": 2, "name": "Old", "currency": "ARS", "principal": "10.00",
                "totalInstallments": 3, "remainingInstallments": 0, "active": false } ] }
            """;
        List<LoanView> result = gatewayReturning(json).fetchActiveLoans(new UserId(1L)).join();
        assertThat(result).containsExactly(new LoanView(1L, "Car", "ARS", "50000.00", 12, 9, true));
    }

    @Test
    void maps_upcoming_payments_with_installment_fields() {
        String json = """
            { "success": true, "data": [
              { "id": 9, "type": "LOAN", "description": "Car #3", "amount": "1500.00", "currency": "ARS",
                "dueDate": "2026-06-10", "installmentNumber": 3, "totalInstallments": 12, "paid": false } ] }
            """;
        List<UpcomingPaymentView> result = gatewayReturning(json)
                .fetchUpcomingPayments(new UserId(1L), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))
                .join();
        assertThat(result).containsExactly(new UpcomingPaymentView(
                9L, "LOAN", "Car #3", "1500.00", "ARS", LocalDate.of(2026, 6, 10), 3, 12, false));
    }

    @Test
    void returns_empty_list_when_data_is_null() {
        String json = "{ \"success\": true, \"data\": null }";
        assertThat(gatewayReturning(json).fetchActiveLoans(new UserId(1L)).join()).isEmpty();
    }

    @Test
    void fetches_loan_installments_as_raw_maps() {
        String json = """
            { "success": true, "data": [
              { "id": 11, "loanId": 1, "installmentNumber": 1, "amount": "1500.00",
                "dueDate": "2026-09-10", "paid": true, "paidDate": "2026-09-09" },
              { "id": 12, "loanId": 1, "installmentNumber": 2, "amount": "1500.00",
                "dueDate": "2026-10-10", "paid": false, "paidDate": null } ] }
            """;
        List<Map<String, Object>> result = gatewayReturning(json)
                .fetchLoanInstallments(new UserId(1L), 1L).join();
        assertThat(result).hasSize(2);
        assertThat(result.getFirst().get("installmentNumber")).isEqualTo(1);
    }

    @Test
    void loan_installments_empty_on_error_body() {
        assertThat(gatewayReturning("{ \"success\": false }")
                .fetchLoanInstallments(new UserId(1L), 9L).join()).isEmpty();
    }
}
