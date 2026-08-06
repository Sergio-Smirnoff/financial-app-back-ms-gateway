package com.financialapp.gateway.web.dto.response.bff;

import com.financialapp.gateway.web.dto.response.SectionResponse;

import java.util.List;
import java.util.Map;

public record BanksBffResponse(
        SectionResponse<Map<String, Object>> kpis,
        SectionResponse<List<Map<String, Object>>> accounts,
        SectionResponse<List<Map<String, Object>>> cards,
        SectionResponse<List<Map<String, Object>>> loans,
        SectionResponse<List<Map<String, Object>>> importHealth,
        SectionResponse<List<Map<String, Object>>> cashDistribution,
        SectionResponse<List<Map<String, Object>>> paymentCalendar
) {}
