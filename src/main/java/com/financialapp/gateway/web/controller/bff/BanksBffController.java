package com.financialapp.gateway.web.controller.bff;

import com.financialapp.commons.core.response.ApiResponse;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.usecase.bff.GetBanksBffUseCase;
import com.financialapp.gateway.web.dto.response.bff.BanksBffResponse;
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
@RequestMapping("/api/v1/bff/banks")
@Tag(name = "BFF - Banks", description = "Bancos Page BFF Composition Endpoint")
public class BanksBffController {

    private final GetBanksBffUseCase getBanksBffUseCase;

    public BanksBffController(GetBanksBffUseCase getBanksBffUseCase) {
        this.getBanksBffUseCase = getBanksBffUseCase;
    }

    @GetMapping
    @Operation(summary = "Get banks page composed sections")
    public Mono<ApiResponse<BanksBffResponse>> getBanks(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(value = "currency", defaultValue = "ARS") String currencyStr,
            @RequestParam(value = "secondary", defaultValue = "none") String secondary) {

        CurrencyView view;
        try {
            view = CurrencyView.valueOf(currencyStr.toUpperCase());
        } catch (Exception e) {
            view = CurrencyView.ARS;
        }

        return Mono.fromFuture(getBanksBffUseCase.execute(new UserId(userId), view, secondary))
                .map(data -> ApiResponse.ok(new BanksBffResponse(
                        BffMapper.toSectionResponse(data.kpis(), BffMapper::toBanksKpisResponse),
                        BffMapper.toSectionResponse(data.accounts(), list -> list.stream().map(BffMapper::toAccountRowResponse).toList()),
                        BffMapper.toSectionResponse(data.cards(), list -> list.stream().map(BffMapper::toCardRowResponse).toList()),
                        BffMapper.toSectionResponse(data.loans(), list -> list.stream().map(BffMapper::toLoanRowResponse).toList()),
                        BffMapper.toSectionResponse(data.importHealth(), list -> list.stream().map(BffMapper::toImportHealthRowResponse).toList()),
                        BffMapper.toSectionResponse(data.cashDistribution(), list -> list.stream().map(BffMapper::toCompositionSliceResponse).toList()),
                        BffMapper.toSectionResponse(data.paymentCalendar(), list -> list.stream().map(BffMapper::toCalendarEntryResponse).toList()))));
    }
}
