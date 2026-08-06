package com.financialapp.gateway.web.controller.bff;

import com.financialapp.commons.core.response.ApiResponse;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.usecase.bff.GetSettingsBffUseCase;
import com.financialapp.gateway.web.dto.response.bff.SettingsBffResponse;
import com.financialapp.gateway.web.mapper.BffMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/bff/settings")
@Tag(name = "BFF - Settings", description = "Ajustes Page BFF Composition Endpoint")
public class SettingsBffController {

    private final GetSettingsBffUseCase getSettingsBffUseCase;

    public SettingsBffController(GetSettingsBffUseCase getSettingsBffUseCase) {
        this.getSettingsBffUseCase = getSettingsBffUseCase;
    }

    @GetMapping
    @Operation(summary = "Get settings page composed sections")
    public Mono<ApiResponse<SettingsBffResponse>> getSettings(@RequestHeader("X-User-Id") Long userId) {

        return Mono.fromFuture(getSettingsBffUseCase.execute(new UserId(userId)))
                .map(data -> ApiResponse.ok(new SettingsBffResponse(
                        BffMapper.toSectionResponse(data.profile()),
                        BffMapper.toSectionResponse(data.preferences()),
                        BffMapper.toSectionResponse(data.fees()),
                        BffMapper.toSectionResponse(data.notificationPrefs()),
                        BffMapper.toSectionResponse(data.sessions()))));
    }
}
