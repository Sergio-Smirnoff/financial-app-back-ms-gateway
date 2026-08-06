package com.financialapp.gateway.web.controller.bff;

import com.financialapp.commons.core.response.ApiResponse;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.usecase.bff.GetTransactionDetailBffUseCase;
import com.financialapp.gateway.domain.usecase.bff.GetTransactionsBffUseCase;
import com.financialapp.gateway.web.dto.response.bff.TransactionDetailBffResponse;
import com.financialapp.gateway.web.dto.response.bff.TransactionsBffResponse;
import com.financialapp.gateway.web.mapper.BffMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/bff/transactions")
@Tag(name = "BFF - Transactions", description = "Movimientos Page BFF Composition Endpoints")
public class TransactionsBffController {

    private final GetTransactionsBffUseCase getTransactionsBffUseCase;
    private final GetTransactionDetailBffUseCase getTransactionDetailBffUseCase;

    public TransactionsBffController(
            GetTransactionsBffUseCase getTransactionsBffUseCase,
            GetTransactionDetailBffUseCase getTransactionDetailBffUseCase) {
        this.getTransactionsBffUseCase = getTransactionsBffUseCase;
        this.getTransactionDetailBffUseCase = getTransactionDetailBffUseCase;
    }

    @GetMapping
    @Operation(summary = "Get paginated transactions list with summary sections")
    public Mono<ApiResponse<TransactionsBffResponse>> getTransactions(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "categories", required = false) List<String> categories,
            @RequestParam(value = "accounts", required = false) List<String> accounts,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "currency", defaultValue = "ARS") String currencyStr,
            @RequestParam(value = "secondary", defaultValue = "none") String secondary) {

        CurrencyView view;
        try {
            view = CurrencyView.valueOf(currencyStr.toUpperCase());
        } catch (Exception e) {
            view = CurrencyView.ARS;
        }

        return Mono.fromFuture(getTransactionsBffUseCase.execute(
                new UserId(userId), page, size, categories, accounts, from, to, view, secondary))
                .map(data -> ApiResponse.ok(new TransactionsBffResponse(
                        BffMapper.toSectionResponse(data.summary()),
                        BffMapper.toSectionResponse(data.page()),
                        BffMapper.toSectionResponse(data.filterOptions()),
                        BffMapper.toSectionResponse(data.uncategorised()))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get single transaction detail with import run lookup")
    public Mono<ApiResponse<TransactionDetailBffResponse>> getTransactionDetail(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable("id") Long transactionId) {

        return Mono.fromFuture(getTransactionDetailBffUseCase.execute(new UserId(userId), transactionId))
                .map(data -> ApiResponse.ok(new TransactionDetailBffResponse(
                        BffMapper.toSectionResponse(data.detail()))));
    }
}
