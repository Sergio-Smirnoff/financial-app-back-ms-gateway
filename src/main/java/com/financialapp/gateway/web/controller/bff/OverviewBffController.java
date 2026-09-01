package com.financialapp.gateway.web.controller.bff;

import com.financialapp.commons.core.response.ApiResponse;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.usecase.bff.GetOverviewBffUseCase;
import com.financialapp.gateway.web.dto.response.bff.OverviewBffResponse;
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
@RequestMapping("/api/v1/bff/overview")
@Tag(name = "BFF - Overview", description = "Resumen Page BFF Composition Endpoint")
public class OverviewBffController {

    private final GetOverviewBffUseCase getOverviewBffUseCase;

    public OverviewBffController(GetOverviewBffUseCase getOverviewBffUseCase) {
        this.getOverviewBffUseCase = getOverviewBffUseCase;
    }

    @GetMapping
    @Operation(summary = "Get overview page composed sections")
    public Mono<ApiResponse<OverviewBffResponse>> getOverview(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(value = "currency", defaultValue = "ARS") String currencyStr,
            @RequestParam(value = "secondary", defaultValue = "none") String secondary) {

        CurrencyView view;
        try {
            view = CurrencyView.valueOf(currencyStr.toUpperCase());
        } catch (Exception e) {
            view = CurrencyView.ARS;
        }

        return Mono.fromFuture(getOverviewBffUseCase.execute(new UserId(userId), view, secondary))
                .map(data -> ApiResponse.ok(new OverviewBffResponse(
                        BffMapper.toSectionResponse(data.kpis(), BffMapper::toOverviewKpisResponse),
                        BffMapper.toSectionResponse(data.netWorth(), BffMapper::toNetWorthResponse),
                        BffMapper.toSectionResponse(data.breakdown(), BffMapper::toBreakdownResponse),
                        BffMapper.toSectionResponse(data.flow(), list -> list.stream().map(BffMapper::toFlowPointResponse).toList()),
                        BffMapper.toSectionResponse(data.committed(), list -> list.stream().map(BffMapper::toCommittedPointResponse).toList()),
                        BffMapper.toSectionResponse(data.upcomingPayments(), list -> list.stream().map(BffMapper::toUpcomingPaymentResponse).toList()),
                        BffMapper.toSectionResponse(data.spendByCategory(), list -> list.stream().map(BffMapper::toCategorySpendResponse).toList()),
                        BffMapper.toSectionResponse(data.latestMovements(), list -> list.stream().map(BffMapper::toTransactionRowResponse).toList()))));
    }
}
