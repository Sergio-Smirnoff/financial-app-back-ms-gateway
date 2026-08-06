package com.financialapp.gateway.web.dto.response.bff;

import com.financialapp.gateway.web.dto.response.SectionResponse;

import java.util.List;
import java.util.Map;

public record ImportsBffResponse(
        SectionResponse<Map<String, Object>> activeRun,
        SectionResponse<List<Map<String, Object>>> history,
        SectionResponse<Map<String, Object>> reconciliation
) {}
