package com.financialapp.gateway.web.dto.response.bff;

import com.financialapp.gateway.web.dto.response.SectionResponse;
import com.financialapp.gateway.web.dto.response.bff.BffWebResponses.*;

import java.util.List;

public record BanksBffResponse(
        SectionResponse<BanksKpisResponse> kpis,
        SectionResponse<List<AccountRowResponse>> accounts,
        SectionResponse<List<CardRowResponse>> cards,
        SectionResponse<List<LoanRowResponse>> loans,
        SectionResponse<List<ImportHealthRowResponse>> importHealth,
        SectionResponse<List<CompositionSliceResponse>> cashDistribution,
        SectionResponse<List<CalendarEntryResponse>> paymentCalendar
) {}
