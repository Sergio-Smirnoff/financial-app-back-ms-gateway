package com.financialapp.gateway.web.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financialapp.gateway.domain.exception.ResourceNotFoundException;
import com.financialapp.gateway.domain.model.bff.TransactionDetailBffData;
import com.financialapp.gateway.domain.usecase.bff.GetTransactionDetailBffUseCase;
import com.financialapp.gateway.domain.usecase.bff.GetTransactionsBffUseCase;
import com.financialapp.gateway.web.controller.bff.TransactionsBffController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock private GetTransactionsBffUseCase getTransactionsBffUseCase;
    @Mock private GetTransactionDetailBffUseCase getTransactionDetailBffUseCase;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient
                .bindToController(new TransactionsBffController(getTransactionsBffUseCase, getTransactionDetailBffUseCase))
                .controllerAdvice(new GlobalExceptionHandler(new ObjectMapper()))
                .build();
    }

    @Test
    void aNonNumericTransactionIdIsABadRequest() {
        client.get().uri("/api/v1/bff/transactions/abc").header("X-User-Id", "1")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.code").isEqualTo("invalid_request");
        verifyNoInteractions(getTransactionDetailBffUseCase);
    }

    @Test
    void aMissingTransactionIsNotFound() {
        when(getTransactionDetailBffUseCase.execute(any(), eq(999L))).thenReturn(
                CompletableFuture.<TransactionDetailBffData>supplyAsync(() -> {
                    throw new ResourceNotFoundException("Transaction", 999L);
                }));

        client.get().uri("/api/v1/bff/transactions/999").header("X-User-Id", "1")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.code").isEqualTo("resource_not_found");
    }

    @Test
    void anUnexpectedFailureIsStillAnInternalError() {
        when(getTransactionDetailBffUseCase.execute(any(), eq(5L)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("boom")));

        client.get().uri("/api/v1/bff/transactions/5").header("X-User-Id", "1")
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.code").isEqualTo("internal_error");
    }
}
