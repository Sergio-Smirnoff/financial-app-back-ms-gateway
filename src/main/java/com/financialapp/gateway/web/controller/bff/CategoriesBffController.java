package com.financialapp.gateway.web.controller.bff;

import com.financialapp.commons.core.response.ApiResponse;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.usecase.bff.GetCategoriesBffUseCase;
import com.financialapp.gateway.web.dto.response.bff.CategoriesBffResponse;
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
@RequestMapping("/api/v1/bff/categories")
@Tag(name = "BFF - Categories", description = "Categorías Page BFF Composition Endpoint")
public class CategoriesBffController {

    private final GetCategoriesBffUseCase getCategoriesBffUseCase;

    public CategoriesBffController(GetCategoriesBffUseCase getCategoriesBffUseCase) {
        this.getCategoriesBffUseCase = getCategoriesBffUseCase;
    }

    @GetMapping
    @Operation(summary = "Get categories page composed sections")
    public Mono<ApiResponse<CategoriesBffResponse>> getCategories(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(value = "currency", defaultValue = "ARS") String currencyStr,
            @RequestParam(value = "secondary", defaultValue = "none") String secondary) {

        CurrencyView view;
        try {
            view = CurrencyView.valueOf(currencyStr.toUpperCase());
        } catch (Exception e) {
            view = CurrencyView.ARS;
        }

        return Mono.fromFuture(getCategoriesBffUseCase.execute(new UserId(userId), view, secondary))
                .map(data -> ApiResponse.ok(new CategoriesBffResponse(
                        BffMapper.toSectionResponse(data.kpis()),
                        BffMapper.toSectionResponse(data.budgets()),
                        BffMapper.toSectionResponse(data.selectedTrend()),
                        BffMapper.toSectionResponse(data.rules()))));
    }
}
