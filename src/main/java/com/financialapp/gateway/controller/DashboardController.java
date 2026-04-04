package com.financialapp.gateway.controller;

import com.financialapp.gateway.aggregator.DashboardAggregator;
import com.financialapp.gateway.model.dto.ApiResponse;
import com.financialapp.gateway.model.dto.DashboardSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Aggregated data from multiple services")
public class DashboardController {

    private final DashboardAggregator dashboardAggregator;

    @GetMapping("/summary")
    @Operation(
            summary = "Dashboard summary",
            description = "Aggregates finance summary, active cards, and recent notifications in a single call"
    )
    public Mono<ResponseEntity<ApiResponse<DashboardSummaryResponse>>> getSummary(
            @RequestHeader("X-User-Id") Long userId) {

        return dashboardAggregator.getDashboardSummary(userId)
                .map(summary -> ResponseEntity.ok(ApiResponse.ok(summary)));
    }
}
