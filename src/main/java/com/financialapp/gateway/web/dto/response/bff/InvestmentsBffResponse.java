package com.financialapp.gateway.web.dto.response.bff;

import com.financialapp.gateway.web.dto.response.SectionResponse;
import com.financialapp.gateway.web.dto.response.bff.BffWebResponses.*;

import java.util.List;

public record InvestmentsBffResponse(
        SectionResponse<List<MarketQuoteResponse>> marketStrip,
        SectionResponse<InvestmentsKpisResponse> kpis,
        SectionResponse<List<EvolutionPointResponse>> evolution,
        SectionResponse<List<PositionRowResponse>> positions,
        SectionResponse<List<CompositionSliceResponse>> composition,
        SectionResponse<List<OperationRowResponse>> recentOperations,
        SectionResponse<List<AlertRowResponse>> alerts
) {}
