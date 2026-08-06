package com.financialapp.gateway.web.dto.response.bff;

import com.financialapp.gateway.web.dto.response.SectionResponse;

import java.util.Map;

public record TransactionDetailBffResponse(
        SectionResponse<Map<String, Object>> detail
) {}
