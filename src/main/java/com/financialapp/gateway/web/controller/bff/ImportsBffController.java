package com.financialapp.gateway.web.controller.bff;

import com.financialapp.commons.core.response.ApiResponse;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.usecase.bff.GetImportsBffUseCase;
import com.financialapp.gateway.web.dto.response.bff.ImportsBffResponse;
import com.financialapp.gateway.web.mapper.BffMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/bff/imports")
@Tag(name = "BFF - Imports", description = "Importaciones Page BFF Composition Endpoint")
public class ImportsBffController {

    private final GetImportsBffUseCase getImportsBffUseCase;

    public ImportsBffController(GetImportsBffUseCase getImportsBffUseCase) {
        this.getImportsBffUseCase = getImportsBffUseCase;
    }

    @GetMapping
    @Operation(summary = "Get imports page composed sections")
    public Mono<ApiResponse<ImportsBffResponse>> getImports(@RequestHeader("X-User-Id") Long userId) {

        return Mono.fromFuture(getImportsBffUseCase.execute(new UserId(userId)))
                .map(data -> ApiResponse.ok(new ImportsBffResponse(
                        BffMapper.toSectionResponse(data.activeRun(), BffMapper::toActiveRunResponse),
                        BffMapper.toSectionResponse(data.history(), list -> list.stream().map(BffMapper::toImportRunRowResponse).toList()),
                        BffMapper.toSectionResponse(data.reconciliation(), list -> list.stream().map(BffMapper::toReconciliationRowResponse).toList()))));
    }
}
