package com.financialapp.gateway.web.controller;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.usecase.dashboard.GetDashboardData;
import com.financialapp.gateway.web.dto.response.ApiResponse;
import com.financialapp.gateway.web.dto.response.DashboardResponse;
import com.financialapp.gateway.web.mapper.DashboardMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Aggregated dashboard data (BFF)")
public class DashboardController {

    private final GetDashboardData getDashboardData;
    private final DashboardMapper mapper;

    public DashboardController(GetDashboardData getDashboardData, DashboardMapper mapper) {
        this.getDashboardData = getDashboardData;
        this.mapper = mapper;
    }

    @GetMapping("/data")
    @Operation(summary = "Get composed dashboard data for the authenticated user")
    public Mono<ApiResponse<DashboardResponse>> data(@RequestHeader("X-User-Id") Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate yearFrom = today.withDayOfYear(1);
        LocalDate yearTo = today.withDayOfYear(today.lengthOfYear());
        LocalDate monthFrom = today.withDayOfMonth(1);
        LocalDate monthTo = today.withDayOfMonth(today.lengthOfMonth());

        return Mono.fromFuture(
                getDashboardData.execute(new UserId(userId), yearFrom, yearTo, monthFrom, monthTo))
                .map(data -> ApiResponse.ok(mapper.toResponse(data)));
    }
}
