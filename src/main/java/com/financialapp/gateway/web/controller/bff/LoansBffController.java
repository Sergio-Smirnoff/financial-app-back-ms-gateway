package com.financialapp.gateway.web.controller.bff;

import com.financialapp.commons.core.response.ApiResponse;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.usecase.bff.GetLoanScheduleBffUseCase;
import com.financialapp.gateway.domain.usecase.bff.GetLoansBffUseCase;
import com.financialapp.gateway.web.dto.response.bff.LoanScheduleBffResponse;
import com.financialapp.gateway.web.dto.response.bff.LoansBffResponse;
import com.financialapp.gateway.web.mapper.BffMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/bff/loans")
@Tag(name = "BFF - Loans", description = "Prestamos Page BFF Composition Endpoint")
public class LoansBffController {

    private final GetLoansBffUseCase getLoansBffUseCase;
    private final GetLoanScheduleBffUseCase getLoanScheduleBffUseCase;

    public LoansBffController(GetLoansBffUseCase getLoansBffUseCase, GetLoanScheduleBffUseCase getLoanScheduleBffUseCase) {
        this.getLoansBffUseCase = getLoansBffUseCase;
        this.getLoanScheduleBffUseCase = getLoanScheduleBffUseCase;
    }

    @GetMapping
    @Operation(summary = "Get loans page composed sections")
    public Mono<ApiResponse<LoansBffResponse>> getLoans(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(value = "currency", defaultValue = "ARS") String currencyStr,
            @RequestParam(value = "secondary", defaultValue = "none") String secondary) {

        CurrencyView view = parseView(currencyStr);

        return Mono.fromFuture(getLoansBffUseCase.execute(new UserId(userId), view, secondary))
                .map(data -> ApiResponse.ok(new LoansBffResponse(
                        BffMapper.toSectionResponse(data.kpis(), BffMapper::toLoansKpisResponse),
                        BffMapper.toSectionResponse(data.loans(), list -> list.stream().map(BffMapper::toLoanDetailRowResponse).toList()),
                        BffMapper.toSectionResponse(data.payFromAccounts(), list -> list.stream().map(BffMapper::toAccountOptionResponse).toList()))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one loan's installment schedule")
    public Mono<ApiResponse<LoanScheduleBffResponse>> getSchedule(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id,
            @RequestParam(value = "currency", defaultValue = "ARS") String currencyStr,
            @RequestParam(value = "secondary", defaultValue = "none") String secondary) {

        CurrencyView view = parseView(currencyStr);

        return Mono.fromFuture(getLoanScheduleBffUseCase.execute(new UserId(userId), id, view, secondary))
                .map(data -> ApiResponse.ok(new LoanScheduleBffResponse(
                        BffMapper.toSectionResponse(data.installments(), list -> list.stream().map(BffMapper::toInstallmentRowResponse).toList()))));
    }

    private static CurrencyView parseView(String currencyStr) {
        try {
            return CurrencyView.valueOf(currencyStr.toUpperCase());
        } catch (Exception e) {
            return CurrencyView.ARS;
        }
    }
}
