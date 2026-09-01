package com.financialapp.gateway.web.dto.response.bff;

import com.financialapp.gateway.web.dto.response.SectionResponse;
import com.financialapp.gateway.web.dto.response.bff.BffWebResponses.*;

import java.util.List;

public record OverviewBffResponse(
        SectionResponse<OverviewKpisResponse> kpis,
        SectionResponse<NetWorthResponse> netWorth,
        SectionResponse<BreakdownResponse> breakdown,
        SectionResponse<List<FlowPointResponse>> flow,
        SectionResponse<List<CommittedPointResponse>> committed,
        SectionResponse<List<UpcomingPaymentResponse>> upcomingPayments,
        SectionResponse<List<CategorySpendResponse>> spendByCategory,
        SectionResponse<List<TransactionRowResponse>> latestMovements
) {}
