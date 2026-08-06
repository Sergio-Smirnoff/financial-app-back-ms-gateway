package com.financialapp.gateway.web.controller.bff;

import com.financialapp.commons.core.response.ApiResponse;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.usecase.bff.GetSearchBffUseCase;
import com.financialapp.gateway.web.dto.response.bff.SearchBffResponse;
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
@RequestMapping("/api/v1/bff/search")
@Tag(name = "BFF - Search", description = "Global ⌘K Search BFF Composition Endpoint")
public class SearchBffController {

    private final GetSearchBffUseCase getSearchBffUseCase;

    public SearchBffController(GetSearchBffUseCase getSearchBffUseCase) {
        this.getSearchBffUseCase = getSearchBffUseCase;
    }

    @GetMapping
    @Operation(summary = "Search movements, positions, and categories for ⌘K palette")
    public Mono<ApiResponse<SearchBffResponse>> search(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam("q") String query) {

        return Mono.fromFuture(getSearchBffUseCase.execute(new UserId(userId), query))
                .map(data -> ApiResponse.ok(new SearchBffResponse(
                        BffMapper.toSectionResponse(data.movements()),
                        BffMapper.toSectionResponse(data.positions()),
                        BffMapper.toSectionResponse(data.categories()))));
    }
}
