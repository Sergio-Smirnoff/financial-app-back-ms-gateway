package com.financialapp.gateway.web.controller.bff;

import com.financialapp.commons.core.response.ApiResponse;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.usecase.bff.GetInvestmentsBffUseCase;
import com.financialapp.gateway.web.dto.response.bff.InvestmentsBffResponse;
import com.financialapp.gateway.web.mapper.BffMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/bff/investments")
@Tag(name = "BFF - Investments", description = "Inversiones Page BFF Composition Endpoint")
public class InvestmentsBffController {

    private final GetInvestmentsBffUseCase getInvestmentsBffUseCase;

    public InvestmentsBffController(GetInvestmentsBffUseCase getInvestmentsBffUseCase) {
        this.getInvestmentsBffUseCase = getInvestmentsBffUseCase;
    }

    @GetMapping
    @Operation(summary = "Get investments page composed sections")
    public Mono<ApiResponse<InvestmentsBffResponse>> getInvestments(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(value = "currency", defaultValue = "ARS") String currencyStr,
            @RequestParam(value = "secondary", defaultValue = "none") String secondary) {

        CurrencyView view;
        try {
            view = CurrencyView.valueOf(currencyStr.toUpperCase());
        } catch (Exception e) {
            view = CurrencyView.ARS;
        }

        return Mono.fromFuture(getInvestmentsBffUseCase.execute(new UserId(userId), view, secondary))
                .map(data -> ApiResponse.ok(new InvestmentsBffResponse(
                        BffMapper.toSectionResponse(data.marketStrip(), list -> list.stream().map(BffMapper::toMarketQuoteResponse).toList()),
                        BffMapper.toSectionResponse(data.kpis(), BffMapper::toInvestmentsKpisResponse),
                        BffMapper.toSectionResponse(data.evolution(), list -> list.stream().map(BffMapper::toEvolutionPointResponse).toList()),
                        BffMapper.toSectionResponse(data.positions(), list -> list.stream().map(BffMapper::toPositionRowResponse).toList()),
                        BffMapper.toSectionResponse(data.composition(), list -> list.stream().map(BffMapper::toCompositionSliceResponse).toList()),
                        BffMapper.toSectionResponse(data.recentOperations(), list -> list.stream().map(BffMapper::toOperationRowResponse).toList()),
                        BffMapper.toSectionResponse(data.alerts(), list -> list.stream().map(BffMapper::toAlertRowResponse).toList()))));
    }
}
