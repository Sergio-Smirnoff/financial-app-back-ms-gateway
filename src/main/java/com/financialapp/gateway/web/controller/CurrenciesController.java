package com.financialapp.gateway.web.controller;

import com.financialapp.commons.core.response.ApiResponse;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.usecase.currency.GetAvailableCurrencies;
import com.financialapp.gateway.web.dto.response.AvailableCurrenciesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bff/currencies")
@Tag(name = "BFF Currencies", description = "Available currency selector options (BFF)")
public class CurrenciesController {

    private final GetAvailableCurrencies getAvailableCurrencies;

    public CurrenciesController(GetAvailableCurrencies getAvailableCurrencies) {
        this.getAvailableCurrencies = getAvailableCurrencies;
    }

    @GetMapping
    @Operation(summary = "Get available currencies and default currency for the authenticated user")
    public Mono<ApiResponse<AvailableCurrenciesResponse>> getCurrencies(@RequestHeader("X-User-Id") Long userId) {
        return Mono.fromFuture(getAvailableCurrencies.execute(new UserId(userId)))
                .map(result -> {
                    List<String> codes = result.available().stream().map(Currency::code).toList();
                    String defCode = result.defaultCurrency().code();
                    return ApiResponse.ok(new AvailableCurrenciesResponse(codes, defCode));
                });
    }
}
